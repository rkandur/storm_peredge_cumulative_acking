package storm.starter.faulttolerance.wordcount.acking;

import java.util.HashMap;
import java.util.Map;
import java.util.Random;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import backtype.storm.spout.SpoutOutputCollector;
import backtype.storm.task.TopologyContext;
import backtype.storm.topology.OutputFieldsDeclarer;
import backtype.storm.topology.base.BaseRichSpout;
import backtype.storm.tuple.Fields;
import backtype.storm.tuple.Values;
import backtype.storm.utils.Utils;

public class AckingRandomSentenceSpout extends BaseRichSpout {
	
	private static final long serialVersionUID = 1L;

	// RK NOTE: this should start the default storm ack tracker since we are
	// passing the tupleID as the message ID which will be used by storm as
	// a key to track the tuples progress.
	SpoutOutputCollector _collector;
	Random _rand;
	Boolean enableStormsTimeoutMechanism_;
	String outputStream_;
	
	// these are intentionally made look like rubbish to define specific
	// frequency structure among the possible word which start with a
	// given alphabet
	private static final String[] sentences = new String[] {
			"cadmium computing color create code cadmium computing color create cadmium computing cadmium computing",
			"ampere angstorm angular angle anomaly ampere angstorm angular angle ampere angstorm ampere angstorm",
			"distributed done difference dance dwarfs distributed done difference dance distributed done distributed done",
			"eat earn enjoy east element eat earn enjoy east eat earn eat earn ",
			"barometer bacteria biology badge badminton barometer bacteria biology badge barometer bacteria barometer bacteria",
			"ampere angstorm angular angle anomaly ampere angstorm angular angle ampere angstorm ampere angstorm",
			"cadmium computing color create code cadmium computing color create cadmium computing cadmium computing",
			"barometer bacteria biology badge badminton barometer bacteria biology badge barometer bacteria barometer bacteria",
			"distributed done difference dance dwarfs distributed done difference dance distributed done distributed done",
			"cadmium computing color create code cadmium computing color create cadmium computing cadmium computing",
			"ampere angstorm angular angle anomaly ampere angstorm angular angle ampere angstorm ampere angstorm",
			"eat earn enjoy east element eat earn enjoy east eat earn eat earn ",
			"barometer bacteria biology badge badminton barometer bacteria biology badge barometer bacteria barometer bacteria",
			"fun four fix follow form fun four fix follow fun four fun four",
			"distributed done difference dance dwarfs distributed done difference dance distributed done distributed done",
			"cadmium computing color create code cadmium computing color create cadmium computing cadmium computing",
			"ampere angstorm angular angle anomaly ampere angstorm angular angle ampere angstorm ampere angstorm",
			"distributed done difference dance dwarfs distributed done difference dance distributed done distributed done",
			"gift great golf giraffe gist gift great golf giraffe gift great gift great",
			"barometer bacteria biology badge badminton barometer bacteria biology badge barometer bacteria barometer bacteria",
			"cadmium computing color create code cadmium computing color create cadmium computing cadmium computing" };
	
	private HashMap<String, Integer> tupleTracker_ = new HashMap<String, Integer>();
	private static final Logger LOG = LoggerFactory.getLogger(AckingRandomSentenceSpout.class);
	
	public AckingRandomSentenceSpout(String stream) {
		outputStream_ = stream;
	}

	@Override
	public void open(Map conf, TopologyContext context,
			SpoutOutputCollector collector) {
		_collector = collector;
		_rand = new Random();
		enableStormsTimeoutMechanism_ = context
				.enableStormDefaultTimeoutMechanism();
	}

	@Override
	public void nextTuple() {

		// this is to kind of achieve randomness as emitted by a realistic
		// source like twitter or some data feed
//		Utils.sleep(Math.abs(_rand.nextInt() % 100));
		Utils.sleep(Math.abs(2000));
		
		int index = _rand.nextInt(sentences.length);
		String sentence = sentences[index];
		String tupleId = new StringBuilder().append(_rand.nextInt()).toString();

		Values vals = new Values(tupleId);
		vals.add(sentence);

		if (enableStormsTimeoutMechanism_) {
			// RKNOTE
			// since we want Storm to track the tuples and its acks here
			// we need to give some messageId to emit (3rd argument).

			// But is this messageId needed in every bolt for acking?
			// or can we just not worry about it as long as the tuple is
			// anchored and acked/failed?
			_collector.emit(outputStream_, vals, tupleId);
		} else {
			_collector.emit(outputStream_, vals);
		}
		
		tupleTracker_.put(tupleId, index);

	}
	
	public void emitTuple(int index) {
		
		Utils.sleep(Math.abs(1000));
		
		String sentence = sentences[index];
		String tupleId = new StringBuilder().append(_rand.nextInt()).toString();

		Values vals = new Values(tupleId);
		vals.add(sentence);

		if (enableStormsTimeoutMechanism_) {
			// since we want Storm to track the tuples and its acks here
			// we need to give some messageId to emit (3rd argument).
			_collector.emit(outputStream_, vals, tupleId);
		} else {
			_collector.emit(outputStream_, vals);
		}
		
		tupleTracker_.put(tupleId, index);
	}

	@Override
	public void declareOutputFields(OutputFieldsDeclarer declarer) {
		// spout_tupleId is needed to carry forward the rule that
		// tuple[0] should always have tupleId
		// we should probably add a new abstraction for AckingSpouts???
		declarer.declareStream(outputStream_, new Fields("spoutTupleId", "word"));
	}

	@Override
	public void fail(Object msgId) {
		LOG.error("Tuple with message ID {" + msgId.toString() + "} has failed");
		if(tupleTracker_.get(msgId.toString()) != null) {
			emitTuple(tupleTracker_.get(msgId.toString()));
			tupleTracker_.remove(msgId.toString());
		}
	}
	
	@Override
	public void ack(Object msgId) {
		tupleTracker_.remove(msgId.toString());
	}
	
}
