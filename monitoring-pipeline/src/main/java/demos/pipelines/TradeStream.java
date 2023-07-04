package demos.pipelines;

import com.hazelcast.core.Hazelcast;
import com.hazelcast.core.HazelcastInstance;
import com.hazelcast.jet.JetService;
import com.hazelcast.jet.Util;
import com.hazelcast.jet.accumulator.LongAccumulator;
import com.hazelcast.jet.aggregate.AggregateOperations;
import com.hazelcast.jet.datamodel.KeyedWindowResult;
import com.hazelcast.jet.kafka.KafkaSources;
import com.hazelcast.jet.pipeline.*;
import com.hazelcast.map.IMap;
import org.apache.kafka.common.serialization.StringDeserializer;
import com.hazelcast.internal.json.Json;
import com.hazelcast.internal.json.JsonObject;

import java.util.Map;
import java.util.Properties;
import java.util.UUID;


public class TradeStream {
    private static final String KAFKA_TOPIC_NAME_TRADES = "trades-topic" ;
    public static final String TRADES_MAP = "trades-map";
    private static final String LOOKUP_TABLE = "lookup-table" ;
    private static final long PRICE_DROP_TRESHOLD = 200;

    public static void main (String[] args) {

        HazelcastInstance hz = Hazelcast.bootstrappedInstance();

        Pipeline p = buildPipeline(hz);
        JetService jet = hz.getJet();

        hz.getJet().newJob(p).join();
    }

    private static Pipeline buildPipeline(HazelcastInstance hz) {

        Properties props = new Properties();
        props.setProperty("bootstrap.servers", "localhost:9092");
        props.setProperty("key.deserializer", StringDeserializer.class.getCanonicalName());
        props.setProperty("value.deserializer", StringDeserializer.class.getCanonicalName());
        props.setProperty("auto.offset.reset", "earliest");
        props.setProperty("group.id", UUID.randomUUID().toString());

        IMap<String, String> tradeEventsMap = hz.getMap(TRADES_MAP);
        IMap<String, String> lookupTable = hz.getMap(LOOKUP_TABLE);

        Pipeline pipeline = Pipeline.create();

        // Read Trade events from kafka topic and put them into Map entries
        // Viewing Raw Data and writing to IMap
       StreamStage<Map.Entry<String, String>> tradeMapValues =
                pipeline.readFrom(KafkaSources.<String, String, Map.Entry<String, String>>kafka(props,
                                record -> Util.entry(record.key(), record.value()),
                                KAFKA_TOPIC_NAME_TRADES))
                       // .withoutTimestamps()
                        .withTimestamps(record -> timestampFromRecord(record.getValue()), 1000)
                        .setName("IMap Values");

       tradeMapValues.writeTo(Sinks.logger(event -> "\nTrade Map key is " + event.getKey() +
                ", Trade Map value is " + event.getValue()));

       StreamStage<Trade> tradeFromTopic = tradeMapValues.map(trade -> jsonToTrade(trade.getValue()));

       // tradeFromTopic.writeTo(Sinks.logger());

       tradeMapValues.writeTo(Sinks.map(tradeEventsMap));

        // Enriching the trade data
        // For each trade - lookup in the lookupTable IMap for the map entry with that symbol
        // and the result of the lookup is merged with the item and logged to console and also written to a map

        StreamStage<EnrichedTrade> enrichedTrade =
                tradeFromTopic.mapUsingIMap(lookupTable,
                                Trade::getSymbol,
                                EnrichedTrade::new)
                        .setName("enriched trades");

        // enrichedTrade.writeTo(Sinks.logger());

        enrichedTrade.writeTo(Sinks.logger( event -> "\n\nEnriched Trade:" +
                ", New tradeId = " + event.getId() +
                ", New symbol = " + event.getSymbol() +
                ", New quantity = " + event.getQuantity() +
                ", New price = " + event.getPrice() +
                ", New tradeTime = " + event.getTimestamp() +
                ", New companyName = " + event.getCompanyName()));

        // Stateful processing - report if a price drops by more than 200
        // First parameter returns the object that holds the state.
        // Jet passes this object (new Long Accumulator instance previousPrice) along with each input item
        // (trade in this case) to second parameter functional mapFn, which can update the object's state.
        // Returns the difference if > 200 otherwise null
        StreamStage<Long> priceDifference = enrichedTrade.mapStateful(
                        LongAccumulator::new,
                        (previousPrice, currentTrade) -> {
                            Long difference = previousPrice.get() - currentTrade.getPrice();
                            previousPrice.set(currentTrade.getPrice());

                            return (difference > PRICE_DROP_TRESHOLD) ? difference : null;
                        })
                .setName("price difference");;

        priceDifference.writeTo(Sinks.logger(event -> "\n\nPrice difference = " + event));

        /*
         * Group the events by symbol. For each symbol, compute the average price over a 10s
         * tumbling window.
         *
         */
        StreamStage<KeyedWindowResult<String, Double>> averagePrices = enrichedTrade
                .groupingKey( entry -> entry.getSymbol())
                .window(WindowDefinition.tumbling(10000))
                .aggregate(AggregateOperations.averagingLong(item -> item.getPrice()))
                .setName("Average Price").peek();

        averagePrices.writeTo(Sinks.logger( event -> "\n\naverage price = " + event.toString()));

        return pipeline;
    }

    private static Trade jsonToTrade(String jsonString) {
        JsonObject object = Json.parse(jsonString).asObject();
        Trade trade = new Trade(
                object.get("id").asString(),
                object.get("timestamp").asLong(),
                object.get("symbol").asString(),
                object.get("quantity").asLong(),
                object.get("price").asLong()
        );

        return trade;
    }

    private static Long timestampFromRecord(String jsonString) {
        JsonObject object = Json.parse(jsonString).asObject();

          return   object.get("timestamp").asLong();
    }


}

