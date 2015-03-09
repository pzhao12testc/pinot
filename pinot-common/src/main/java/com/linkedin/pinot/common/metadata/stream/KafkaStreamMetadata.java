package com.linkedin.pinot.common.metadata.stream;

import java.util.Arrays;
import java.util.Map;

import com.linkedin.pinot.common.utils.CommonConstants;
import com.linkedin.pinot.common.utils.StringUtil;
import com.linkedin.pinot.common.utils.CommonConstants.Helix.DataSource.Realtime.Kafka.ConsumerType;


public class KafkaStreamMetadata implements StreamMetadata {

  private final String _kafkaTopicName;
  private final ConsumerType _consumerType;
  private final String _decoderClass;
  private final Map<String, String> _kafkaConfig;

  public KafkaStreamMetadata(Map<String, String> dataResource) {
    _kafkaConfig =
        dataResource;
    _consumerType = ConsumerType.valueOf(dataResource.get(
        StringUtil.join(".", CommonConstants.Helix.DataSource.STREAM,
            CommonConstants.Helix.DataSource.Realtime.Kafka.CONSUMER_TYPE)));
    _kafkaTopicName = dataResource.get(
        StringUtil.join(".", CommonConstants.Helix.DataSource.STREAM,
            CommonConstants.Helix.DataSource.Realtime.Kafka.TOPIC_NAME));
    _decoderClass = dataResource.get(
        StringUtil.join(".", CommonConstants.Helix.DataSource.STREAM,
            CommonConstants.Helix.DataSource.Realtime.Kafka.DECODER_CLASS));
  }

  public String getKafkaTopicName() {
    return _kafkaTopicName;
  }

  public ConsumerType getConsumerType() {
    return _consumerType;
  }

  public Map<String, String> getKafkaConfigs() {
    return _kafkaConfig;
  }

  public String getDecoderClass() {
    return _decoderClass;
  }

  public String toString() {
    final StringBuilder result = new StringBuilder();
    String newline = "\n";
    result.append(this.getClass().getName());
    result.append(" Object {");
    result.append(newline);
    String[] keys = _kafkaConfig.keySet().toArray(new String[0]);
    Arrays.sort(keys);
    for (final String key : keys) {
      if (key.startsWith(StringUtil.join(".", CommonConstants.Helix.DataSource.STREAM, CommonConstants.Helix.DataSource.KAFKA))) {
        result.append("  ");
        result.append(key);
        result.append(": ");
        result.append(_kafkaConfig.get(key));
        result.append(newline);
      }
    }
    result.append("}");

    return result.toString();
  }

  public Map<String, String> toMap() {
    return _kafkaConfig;
  }
}
