package com.streams.favourite.colour;

import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.common.serialization.Serdes;
import org.apache.kafka.streams.KafkaStreams;
import org.apache.kafka.streams.KeyValue;
import org.apache.kafka.streams.StreamsBuilder;
import org.apache.kafka.streams.StreamsConfig;
import org.apache.kafka.streams.kstream.KStream;
import org.apache.kafka.streams.kstream.KTable;
import org.apache.kafka.streams.kstream.Named;
import org.apache.kafka.streams.kstream.Produced;
import java.util.Arrays;
import java.util.Properties;

public class FavouriteColourApp {

    public static void main(String[] args) {
        Properties config = new Properties();
        config.put(StreamsConfig.APPLICATION_ID_CONFIG,"streams-favourite-colour");
        config.put(StreamsConfig.BOOTSTRAP_SERVERS_CONFIG,"localhost:9092");
        config.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG,"earliest");
        config.put(StreamsConfig.DEFAULT_KEY_SERDE_CLASS_CONFIG, Serdes.String().getClass());
        config.put(StreamsConfig.DEFAULT_VALUE_SERDE_CLASS_CONFIG, Serdes.String().getClass());

        // we disable the cache to demonstrate all the "steps" involved in the transformation - not recommended in prod
        config.put(StreamsConfig.CACHE_MAX_BYTES_BUFFERING_CONFIG,"0");

        StreamsBuilder builder = new StreamsBuilder();
        // Step 1: We create the topic of users keys to colours
        KStream<String,String> textLines = builder.stream("favourite-colour-input");

        KStream<String,String> usersAndColours = textLines
                // 1 - we ensure that a comma is here as we will split on it
                .filter((key,value) -> {
                    System.out.println("Filter output: "+key+" "+value);
                    return value.contains(",");
                })
                // 2 - we select a key that will be the user id (lowercase for safety)
                .selectKey((key,value) -> {
                    System.out.println("Select key output: "+key+" "+value);
                    return value.split(",")[0].toLowerCase();
                })
                // 3 - we get the colour from the value (lowercase for safety)
                .mapValues(value -> {
                    System.out.println("Map values output: "+value);
                    return value.split(",")[1].toLowerCase();
                })
                // 4 - we filter undesired colours (could be a data sanitization step
                .filter((user,colour) -> {
                    System.out.println("Second Filter output: "+user+" "+colour);
                    return Arrays.asList("green","red","blue").contains(colour);
                });

        usersAndColours.to("user-keys-and-colours");

        // step 2 - we read that topic as a KTable so that updates are read correctly
        KTable<String, String> usersAndColoursTable = builder.table("user-keys-and-colours");

        // step 3 - we count the occurrence of colours
        KTable<String,Long> favouriteColours = usersAndColoursTable
                // 5 - we group by colour within the KTable
                .groupBy((user,colour) -> new KeyValue<>(colour,colour))
                .count(Named.as("CountsByColours"));

        // 6 - we output the results to a Kafka Topic - don't forget the serializers
        favouriteColours.toStream().to("favourite-colour-output", Produced.with(Serdes.String(), Serdes.Long()));

        KafkaStreams streams = new KafkaStreams(builder.build(), config);
        // only do this in dev - not in prod
        streams.cleanUp();
        streams.start();

        // print the topology
        streams.localThreadsMetadata().forEach(data -> System.out.println(data));

        // shutdown hook to correctly close the streams application
        Runtime.getRuntime().addShutdownHook(new Thread(streams::close));
    }
}
