# Storm HDFS

Storm components for interacting with HDFS file systems


## Usage
The following example will write pipe("|")-delimited files to the HDFS path hdfs://localhost:54310/foo. After every
1,000 tuples it will sync filesystem, making that data visible to other HDFS clients. It will rotate files when they
reach 5 megabytes in size.

```java
// use "|" instead of "," for field delimiter
RecordFormat format = new DelimitedRecordFormat()
        .withFieldDelimiter("|");

// sync the filesystem after every 1k tuples
SyncPolicy syncPolicy = new CountSyncPolicy(1000);

// rotate files when they reach 5MB
FileRotationPolicy rotationPolicy = new FileSizeRotationPolicy(5.0f, Units.MB);

FileNameFormat fileNameFormat = new DefaultFileNameFormat()
        .withPath("/foo/");

HdfsBolt bolt = new HdfsBolt()
        .withFsUrl("hdfs://localhost:54310")
        .withFileNameFormat(fileNameFormat)
        .withRecordFormat(format)
        .withRotationPolicy(rotationPolicy)
        .withSyncPolicy(syncPolicy);
```

### Packaging a Topology
When packaging your topology, it's important that you use the [maven-shade-plugin]() as opposed to the
[maven-assembly-plugin]().

The shade plugin provides facilities for merging JAR manifest entries, which the hadoop client leverages for URL scheme
resolution.

If you experience errors such as the following:

```
java.lang.RuntimeException: Error preparing HdfsBolt: No FileSystem for scheme: hdfs
```

it's an indication that your topology jar file isn't packaged properly.

If you are using maven to create your topology jar, you should use the following `maven-shade-plugin` configuration to
create your topology jar:

```xml
<plugin>
    <groupId>org.apache.maven.plugins</groupId>
    <artifactId>maven-shade-plugin</artifactId>
    <version>1.4</version>
    <configuration>
        <createDependencyReducedPom>true</createDependencyReducedPom>
    </configuration>
    <executions>
        <execution>
            <phase>package</phase>
            <goals>
                <goal>shade</goal>
            </goals>
            <configuration>
                <transformers>
                    <transformer
                            implementation="org.apache.maven.plugins.shade.resource.ServicesResourceTransformer"/>
                    <transformer
                            implementation="org.apache.maven.plugins.shade.resource.ManifestResourceTransformer">
                        <mainClass></mainClass>
                    </transformer>
                </transformers>
            </configuration>
        </execution>
    </executions>
</plugin>

```

### Specifying a Hadoop Version
By default, storm-hdfs uses the following Hadoop dependencies:

```xml
<dependency>
    <groupId>org.apache.hadoop</groupId>
    <artifactId>hadoop-client</artifactId>
    <version>2.2.0</version>
    <exclusions>
        <exclusion>
            <groupId>org.slf4j</groupId>
            <artifactId>slf4j-log4j12</artifactId>
        </exclusion>
    </exclusions>
</dependency>
<dependency>
    <groupId>org.apache.hadoop</groupId>
    <artifactId>hadoop-hdfs</artifactId>
    <version>2.2.0</version>
    <exclusions>
        <exclusion>
            <groupId>org.slf4j</groupId>
            <artifactId>slf4j-log4j12</artifactId>
        </exclusion>
    </exclusions>
</dependency>
```

If you are using a different version of Hadoop, you should exclude the Hadoop libraries from the storm-hdfs dependency
and add the dependencies for your preferred version in your pom.

Hadoop client version incompatibilites can manifest as errors like:

```
com.google.protobuf.InvalidProtocolBufferException: Protocol message contained an invalid tag (zero)
```

## Customization

### Record Formats
Record format can be controlled by providing an implementation of the `org.apache.storm.hdfs.format.RecordFormat`
interface:

```java
public interface RecordFormat extends Serializable {
    byte[] format(Tuple tuple);
}
```

The provided `org.apache.storm.hdfs.format.DelimitedRecordFormat` is capable of producing formats such as CSV and
tab-delimited files.


### File Naming
File naming can be controlled by providing an implementation of the `org.apache.storm.hdfs.format.FileNameFormat`
interface:

```java
public interface FileNameFormat extends Serializable {
    void prepare(Map conf, TopologyContext topologyContext);
    String getName(long rotation, long timeStamp);
    String getPath();
}
```

The provided `org.apache.storm.hdfs.format.DefaultFileNameFormat`  will create file names with the following format:

     {prefix}{componentId}-{taskId}-{rotationNum}-{timestamp}{extension}

For example:

     MyBolt-5-7-1390579837830.txt

By default, prefix is empty and extenstion is ".txt".



### Sync Policies
Sync policies allow you to control when buffered data is flushed to the underlying filesystem (thus making it available
to clients reading the data) by implementing the `org.apache.storm.hdfs.sync.SyncPolicy` interface:

```java
public interface SyncPolicy extends Serializable {
    boolean mark(Tuple tuple, long offset);
    void reset();
}
```
The `HdfsBolt` will call the `mark()` method for every tuple it processes. Returning `true` will trigger the `HdfsBolt`
to perform a sync/flush, after which it will call the `reset()` method.

The `org.apache.storm.hdfs.sync.CountSyncPolicy` class simply triggers a sync after the specified number of tuples have
been processed.

### File Rotation Policies
Similar to sync policies, file rotation policies allow you to control when data files are rotated by providing a
`org.apache.storm.hdfs.rotation.FileRotation` interface:

```java
public interface FileRotationPolicy extends Serializable {
    boolean mark(Tuple tuple, long offset);
    void reset();
}
``` 

The `org.apache.storm.hdfs.rotation.FileSizeRotationPolicy` implementation allows you to trigger file rotation when
data files reach a specific file size:

```java
FileRotationPolicy rotationPolicy = new FileSizeRotationPolicy(5.0f, Units.MB);
```

### File Rotation Actions
Both the HDFS bolt and Trident State implementation allow you to register any number of `RotationAction`s.
What `RotationAction`s do is provide a hook to allow you to perform some action right after a file is rotated. For
example, moving a file to a different location or renaming it.


```java
public interface RotationAction extends Serializable {
    void execute(FileSystem fileSystem, Path filePath) throws IOException;
}
```

Storm-HDFS includes a simple action that will move a file after rotation:

```java
public class MoveFileAction implements RotationAction {
    private static final Logger LOG = LoggerFactory.getLogger(MoveFileAction.class);

    private String destination;

    public MoveFileAction withDestination(String destDir){
        destination = destDir;
        return this;
    }

    @Override
    public void execute(FileSystem fileSystem, Path filePath) throws IOException {
        Path destPath = new Path(destination, filePath.getName());
        LOG.info("Moving file {} to {}", filePath, destPath);
        boolean success = fileSystem.rename(filePath, destPath);
        return;
    }
}
```

If you are using Trident and sequence files you can do something like this:

```java
        HdfsState.Options seqOpts = new HdfsState.SequenceFileOptions()
                .withFileNameFormat(fileNameFormat)
                .withSequenceFormat(new DefaultSequenceFormat("key", "data"))
                .withRotationPolicy(rotationPolicy)
                .withFsUrl("hdfs://localhost:54310")
                .addRotationAction(new MoveFileAction().withDestination("/dest2/"));
```


## Support for HDFS Sequence Files

The `org.apache.storm.hdfs.bolt.SequenceFileBolt` class allows you to write storm data to HDFS sequence files:

```java
        // sync the filesystem after every 1k tuples
        SyncPolicy syncPolicy = new CountSyncPolicy(1000);

        // rotate files when they reach 5MB
        FileRotationPolicy rotationPolicy = new FileSizeRotationPolicy(5.0f, Units.MB);

        FileNameFormat fileNameFormat = new DefaultFileNameFormat()
                .withExtension(".seq")
                .withPath("/data/");

        // create sequence format instance.
        DefaultSequenceFormat format = new DefaultSequenceFormat("timestamp", "sentence");

        SequenceFileBolt bolt = new SequenceFileBolt()
                .withFsUrl("hdfs://localhost:54310")
                .withFileNameFormat(fileNameFormat)
                .withSequenceFormat(format)
                .withRotationPolicy(rotationPolicy)
                .withSyncPolicy(syncPolicy)
                .withCompressionType(SequenceFile.CompressionType.RECORD)
                .withCompressionCodec("deflate");
```

The `SequenceFileBolt` requires that you provide a `org.apache.storm.hdfs.bolt.format.SequenceFormat` that maps tuples to
key/value pairs:

```java
public interface SequenceFormat extends Serializable {
    Class keyClass();
    Class valueClass();

    Writable key(Tuple tuple);
    Writable value(Tuple tuple);
}
```

## Trident API
storm-hdfs also includes a Trident `state` implementation for writing data to HDFS, with an API that closely mirrors
that of the bolts.

 ```java
         Fields hdfsFields = new Fields("field1", "field2");

         FileNameFormat fileNameFormat = new DefaultFileNameFormat()
                 .withPath("/trident")
                 .withPrefix("trident")
                 .withExtension(".txt");

         RecordFormat recordFormat = new DelimitedRecordFormat()
                 .withFields(hdfsFields);

         FileRotationPolicy rotationPolicy = new FileSizeRotationPolicy(5.0f, FileSizeRotationPolicy.Units.MB);

        HdfsState.Options options = new HdfsState.HdfsFileOptions()
                .withFileNameFormat(fileNameFormat)
                .withRecordFormat(recordFormat)
                .withRotationPolicy(rotationPolicy)
                .withFsUrl("hdfs://localhost:54310");

         StateFactory factory = new HdfsStateFactory().withOptions(options);

         TridentState state = stream
                 .partitionPersist(factory, hdfsFields, new HdfsUpdater(), new Fields());
 ```

 To use the sequence file `State` implementation, use the `HdfsState.SequenceFileOptions`:

 ```java
        HdfsState.Options seqOpts = new HdfsState.SequenceFileOptions()
                .withFileNameFormat(fileNameFormat)
                .withSequenceFormat(new DefaultSequenceFormat("key", "data"))
                .withRotationPolicy(rotationPolicy)
                .withFsUrl("hdfs://localhost:54310")
                .addRotationAction(new MoveFileAction().toDestination("/dest2/"));
```


## License

Licensed to the Apache Software Foundation (ASF) under one
or more contributor license agreements.  See the NOTICE file
distributed with this work for additional information
regarding copyright ownership.  The ASF licenses this file
to you under the Apache License, Version 2.0 (the
"License"); you may not use this file except in compliance
with the License.  You may obtain a copy of the License at

  http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing,
software distributed under the License is distributed on an
"AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
KIND, either express or implied.  See the License for the
specific language governing permissions and limitations
under the License.

## Committer Sponsors

 * P. Taylor Goetz ([ptgoetz@apache.org](mailto:ptgoetz@apache.org))
 * Bobby Evans ([bobby@apache.org](mailto:bobby@apache.org))
