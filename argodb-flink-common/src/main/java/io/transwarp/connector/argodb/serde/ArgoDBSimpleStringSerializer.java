package io.transwarp.connector.argodb.serde;


public class ArgoDBSimpleStringSerializer implements ArgoDBRecordSerializationSchema<String[]> {


  @Override
  public byte[][] serialize(String[] element, Long timestamp) {
    return new byte[0][];
  }

  @Override
  public String[] serialize(String[] element) {
    String[] res = new String[element.length];
    System.arraycopy(element, 0, res, 0, element.length);
    return res;
  }

}
