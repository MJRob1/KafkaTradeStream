package tradegenerator;

import com.hazelcast.client.HazelcastClient;
import com.hazelcast.client.config.ClientConfig;
import com.hazelcast.core.HazelcastInstance;
import com.hazelcast.core.HazelcastJsonValue;
import com.hazelcast.map.IMap;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.log4j.Logger;
import org.json.simple.JSONObject;

import java.util.Arrays;
import java.util.List;

import static java.lang.Thread.sleep;

public class TradeGenerator {

    private static final String TOPIC_NAME = "trades-topic";
    private static class ApplicationMessageHandlerImpl implements KafkaMessageHandler{

        /**
         * The Log.
         */
        static Logger log = Logger.getLogger(ApplicationMessageHandlerImpl.class.getName());

        @Override
        public void processMessage(String topicName, ConsumerRecord<String, String> message) throws Exception {
            String source = KafkaMessageHandlerImpl.class.getName();
            JSONObject obj = MessageHelper.getMessageLogEntryJSON(source, topicName,message.key(),message.value());
            System.out.println(obj.toJSONString());
            log.info(obj.toJSONString());
        }
    }

    public static void main(String[] args) throws Exception {

        // Start the kafka producer
        System.out.println("Starting the Producer\n");
        new SimpleProducer().runAlways(TOPIC_NAME, new ApplicationMessageHandlerImpl());

    }

}
