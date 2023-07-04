package refdataloader;

import com.hazelcast.client.HazelcastClient;
import com.hazelcast.client.config.ClientConfig;
import com.hazelcast.core.HazelcastInstance;
import com.hazelcast.map.IMap;

public class RefdataLoader {

    private static final String TRADES_MAP = "trades-map";
    private static final String LOOKUP_TABLE = "lookup-table" ;


    private static final String TRADES_MAPPING_SQL =  "CREATE OR REPLACE MAPPING " + "\"" + TRADES_MAP + "\" " +
            "TYPE IMap OPTIONS (" +
            "'keyFormat' = 'varchar'," +
            "'valueFormat' = 'varchar')";

    public static void main(String[] args) throws Exception {
        ClientConfig clientConfig = new ClientConfig();
        clientConfig.setClusterName("dev");
        clientConfig.getNetworkConfig().addAddress("127.0.0.1");

        HazelcastInstance hzClient = HazelcastClient.newHazelcastClient(clientConfig);


        hzClient.getSql().execute(TRADES_MAPPING_SQL);

        IMap<String, String> lookupTable = hzClient.getMap(LOOKUP_TABLE);
        lookupTable.put("AAPL", "Apple Inc. - Common Stock");
        lookupTable.put("GOOGL", "Alphabet Inc.");
        lookupTable.put("MSFT", "Microsoft Corporation");

        System.out.println("Finished loading reference data");
        hzClient.shutdown();

    }

}
