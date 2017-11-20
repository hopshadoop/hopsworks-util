package io.hops.util.dela;

import com.twitter.bijection.Injection;
import com.twitter.bijection.avro.GenericAvroCodecs;
import io.hops.util.HopsProcess;
import io.hops.util.HopsProcessType;
import io.hops.util.HopsUtil;
import java.util.Map;
import java.util.Properties;
import java.util.logging.Logger;
import org.apache.avro.Schema;
import org.apache.avro.generic.GenericData;
import org.apache.avro.generic.GenericRecord;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;

/**
 * Hops Dela wrapper for a Kafka producer.
 * 
 */
public class DelaProducer extends HopsProcess {

  private static final Logger LOGGER = Logger.getLogger(DelaProducer.class.getName());

  private final KafkaProducer<String, byte[]> producer;
  private final Injection<GenericRecord, byte[]> recordInjection;

  /**
   *
   * @param topic
   * @param schema
   * @param lingerDelay
   */
  public DelaProducer(String topic, Schema schema, long lingerDelay) {
    super(HopsProcessType.PRODUCER, topic, schema);
    Properties props = HopsUtil.getKafkaProperties().defaultProps();
    props.put(ProducerConfig.CLIENT_ID_CONFIG, "DelaProducer");
    props.put(ProducerConfig.LINGER_MS_CONFIG, lingerDelay);
    producer = new KafkaProducer<>(props);
    recordInjection = GenericAvroCodecs.toBinary(schema);
  }

  /**
   *
   * @param messageFields
   */
  public void produce(Map<String, Object> messageFields) {
    //create the avro message
    GenericData.Record avroRecord = new GenericData.Record(schema);
    for (Map.Entry<String, Object> message : messageFields.entrySet()) {
      //TODO: Check that messageFields are in avro record
      avroRecord.put(message.getKey(), message.getValue());
    }
    produce(avroRecord);
  }

  /**
   *
   * @param avroRecord
   */
  public void produce(GenericRecord avroRecord) {
    byte[] bytes = recordInjection.apply(avroRecord);
    produce(bytes);
  }

  /**
   *
   * @param byteRecord
   */
  public void produce(byte[] byteRecord) {
    ProducerRecord<String, byte[]> record = new ProducerRecord<>(topic, byteRecord);
    producer.send(record);
  }

  /**
   *
   * @param avroRecord
   * @return
   */
  public byte[] prepareRecord(GenericRecord avroRecord) {
    byte[] bytes = recordInjection.apply(avroRecord);
    return bytes;
  }

  /**
   *
   */
  @Override
  public void close() {
    producer.close();
  }
}
