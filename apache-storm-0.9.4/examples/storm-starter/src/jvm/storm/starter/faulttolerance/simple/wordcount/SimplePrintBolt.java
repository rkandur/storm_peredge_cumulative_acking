package storm.starter.faulttolerance.simple.wordcount;

import java.util.HashMap;
import java.util.Map;

import backtype.storm.task.OutputCollector;
import backtype.storm.task.TopologyContext;
import backtype.storm.topology.OutputFieldsDeclarer;
import backtype.storm.topology.base.BaseRichBolt;
import backtype.storm.tuple.Tuple;
import backtype.storm.utils.Utils;

public class SimplePrintBolt extends BaseRichBolt {

	private static final long serialVersionUID = 1L;
	// this just gives you index in tuple which holds the incoming
	// message
	private static final int MESSAGE_INDEX_1 = 0;
	private static final int MESSAGE_INDEX_2 = 1;
	private Boolean enableStormsTimeoutMechanism_;
	private OutputCollector collector_;
	
	HashMap<String, Integer> counts_ = new HashMap<String, Integer>();

	@Override
	public void prepare(Map stormConf, TopologyContext context,
			OutputCollector collector) {
		collector_ = collector;
		enableStormsTimeoutMechanism_ = context.enableStormDefaultTimeoutMechanism();
	}

	@Override
	public void execute(Tuple tuple) {
		
		if (enableStormsTimeoutMechanism_) {
			collector_.ack(tuple);
		}
		
		// just like that
		Utils.sleep(500);
		
		// As Spout is sending directly to this bolt and it provides no
		// other fancy stuff other than the message
		// which is a simple string in this case.
		String word = tuple.getString(MESSAGE_INDEX_1);
		Integer count = tuple.getInteger(MESSAGE_INDEX_2);
		
		if(counts_.containsKey(word)) {
			count += counts_.get(word);
		}
		counts_.put(word, count);
		
		System.out.println("Super Count for characer {" + word + "} -> count {" + count + "}");
		
	}

	@Override
	public void declareOutputFields(OutputFieldsDeclarer declarer) {
	}
	
}
