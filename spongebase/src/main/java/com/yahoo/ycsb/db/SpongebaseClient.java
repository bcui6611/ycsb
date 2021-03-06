/**
 * Copyright (c) 2015 Sponge Data Inc. All rights reserved.
 */

package com.yahoo.ycsb.db;
 
import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.yahoo.ycsb.ByteIterator;
import com.yahoo.ycsb.DB;
import com.yahoo.ycsb.DBException;
import com.yahoo.ycsb.Status;
import com.yahoo.ycsb.StringByteIterator;

import net.spy.memcached.PersistTo;
import net.spy.memcached.ReplicateTo;
import net.spy.memcached.internal.OperationFuture;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.StringWriter;
import java.io.Writer;
import java.net.URI;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.Vector;

/**
 * A class that wraps the SpongebaseClient to allow it to be interfaced with YCSB.
 * This class extends {@link DB} and implements the database interface used by YCSB client.
 */
public class SpongebaseClient extends DB {
  public static final String URL_PROPERTY = "spongebase.url";
  public static final String BUCKET_PROPERTY = "spongebase.bucket";
  public static final String PASSWORD_PROPERTY = "spongebase.password";
  public static final String CHECKF_PROPERTY = "spongebase.checkFutures";
  public static final String PERSIST_PROPERTY = "spongebase.persistTo";
  public static final String REPLICATE_PROPERTY = "spongebase.replicateTo";
  public static final String JSON_PROPERTY = "spongebase.json";
  public static final String DESIGN_DOC_PROPERTY = "spongebase.ddoc";
  public static final String VIEW_PROPERTY = "spongebase.view";
  public static final String STALE_PROPERTY = "spongebase.stale";
  public static final String SCAN_PROPERTY = "scanproportion";

  public static final String SCAN_PROPERTY_DEFAULT = "0.0";

  protected static final ObjectMapper JSON_MAPPER = new ObjectMapper();

  private com.sponge.client.SpongebaseClient client;
  private PersistTo persistTo;
  private ReplicateTo replicateTo;
  private boolean checkFutures;
  private boolean useJson;
  private final Logger log = LoggerFactory.getLogger(getClass());

  @Override
  public void init() throws DBException {
    Properties props = getProperties();

    String url = props.getProperty(URL_PROPERTY, "http://127.0.0.1:8091/pools");
    String bucket = props.getProperty(BUCKET_PROPERTY, "volume1.kv");
    String password = props.getProperty(PASSWORD_PROPERTY, "");

    checkFutures = props.getProperty(CHECKF_PROPERTY, "true").equals("true");
    useJson = props.getProperty(JSON_PROPERTY, "true").equals("true");

    persistTo = parsePersistTo(props.getProperty(PERSIST_PROPERTY, "0"));
    replicateTo = parseReplicateTo(props.getProperty(REPLICATE_PROPERTY, "0"));

    Double scanproportion = Double.valueOf(props.getProperty(SCAN_PROPERTY, SCAN_PROPERTY_DEFAULT));

    Properties systemProperties = System.getProperties();
    systemProperties.put("net.spy.log.LoggerImpl", "net.spy.memcached.compat.log.SLF4JLogger");
    System.setProperties(systemProperties);

    try {
      client = new com.sponge.client.SpongebaseClient(Arrays.asList(new URI(url)), bucket, password);
    } catch (Exception e) {
      throw new DBException("Could not create SpongebaseClient object.", e);
    }

    if (scanproportion > 0) {
      throw new DBException(String.format("scan operation not supported"));
    }
  }

  /**
   * Parse the replicate property into the correct enum.
   *
   * @param property the stringified property value.
   * @throws DBException if parsing the property did fail.
   * @return the correct enum.
   */
  private ReplicateTo parseReplicateTo(final String property) throws DBException {
    int value = Integer.parseInt(property);

    switch (value) {
    case 0:
      return ReplicateTo.ZERO;
    case 1:
      return ReplicateTo.ONE;
    case 2:
      return ReplicateTo.TWO;
    case 3:
      return ReplicateTo.THREE;
    default:
      throw new DBException(REPLICATE_PROPERTY + " must be between 0 and 3");
    }
  }

  /**
   * Parse the persist property into the correct enum.
   *
   * @param property the stringified property value.
   * @throws DBException if parsing the property did fail.
   * @return the correct enum.
   */
  private PersistTo parsePersistTo(final String property) throws DBException {
    int value = Integer.parseInt(property);

    switch (value) {
    case 0:
      return PersistTo.ZERO;
    case 1:
      return PersistTo.ONE;
    case 2:
      return PersistTo.TWO;
    case 3:
      return PersistTo.THREE;
    case 4:
      return PersistTo.FOUR;
    default:
      throw new DBException(PERSIST_PROPERTY + " must be between 0 and 4");
    }
  }

  /**
   * Shutdown the client.
   */
  @Override
  public void cleanup() {
    client.shutdown();
  }

  @Override
  public Status read(final String table, final String key, final Set<String> fields,
                     final HashMap<String, ByteIterator> result) {
    String formattedKey = formatKey(table, key);

    try {
      Object loaded = client.get(formattedKey);

      if (loaded == null) {
        return Status.ERROR;
      }

      decode(loaded, fields, result);
      return Status.OK;
    } catch (Exception e) {
      if (log.isErrorEnabled()) {
        log.error("Could not read value for key " + formattedKey, e);
      }
      return Status.ERROR;
    }
  }

  @Override
  public Status scan(final String table, final String startkey, final int recordcount, final Set<String> fields,
                     final Vector<HashMap<String, ByteIterator>> result) {
    return Status.ERROR;
  }

  @Override
  public Status update(final String table, final String key, final HashMap<String, ByteIterator> values) {
    String formattedKey = formatKey(table, key);

    try {
      final OperationFuture<Boolean> future = client.replace(formattedKey, encode(values), persistTo, replicateTo);
      return checkFutureStatus(future);
    } catch (Exception e) {
      if (log.isErrorEnabled()) {
        log.error("Could not update value for key " + formattedKey, e);
      }
      return Status.ERROR;
    }
  }

  @Override
  public Status insert(final String table, final String key, final HashMap<String, ByteIterator> values) {
    String formattedKey = formatKey(table, key);

    try {
      final OperationFuture<Boolean> future = client.add(formattedKey, encode(values), persistTo, replicateTo);
      return checkFutureStatus(future);
    } catch (Exception e) {
      if (log.isErrorEnabled()) {
        log.error("Could not insert value for key " + formattedKey, e);
      }
      return Status.ERROR;
    }
  }

  @Override
  public Status delete(final String table, final String key) {
    String formattedKey = formatKey(table, key);

    try {
      final OperationFuture<Boolean> future = client.delete(formattedKey, persistTo, replicateTo);
      return checkFutureStatus(future);
    } catch (Exception e) {
      if (log.isErrorEnabled()) {
        log.error("Could not delete value for key " + formattedKey, e);
      }
      return Status.ERROR;
    }
  }

  /**
   * Prefix the key with the given prefix, to establish a unique namespace.
   *
   * @param prefix the prefix to use.
   * @param key the actual key.
   * @return the formatted and prefixed key.
   */
  private String formatKey(final String prefix, final String key) {
    return prefix + ":" + key;
  }

  /**
   * Wrapper method that either inspects the future or not.
   *
   * @param future the future to potentially verify.
   * @return the status of the future result.
   */
  private Status checkFutureStatus(final OperationFuture<?> future) {
    if (checkFutures) {
      return future.getStatus().isSuccess() ? Status.OK : Status.ERROR;
    } else {
      return Status.OK;
    }
  }

  /**
   * Decode the object from server into the storable result.
   *
   * @param source the loaded object.
   * @param fields the fields to check.
   * @param dest the result passed back to the ycsb core.
   */
  private void decode(final Object source, final Set<String> fields, final HashMap<String, ByteIterator> dest) {
    if (useJson) {
      try {
        JsonNode json = JSON_MAPPER.readTree((String) source);
        boolean checkFields = fields != null && !fields.isEmpty();
        for (Iterator<Map.Entry<String, JsonNode>> jsonFields = json.fields(); jsonFields.hasNext();) {
          Map.Entry<String, JsonNode> jsonField = jsonFields.next();
          String name = jsonField.getKey();
          if (checkFields && fields.contains(name)) {
            continue;
          }
          JsonNode jsonValue = jsonField.getValue();
          if (jsonValue != null && !jsonValue.isNull()) {
            dest.put(name, new StringByteIterator(jsonValue.asText()));
          }
        }
      } catch (Exception e) {
        throw new RuntimeException("Could not decode JSON");
      }
    } else {
      HashMap<String, String> converted = (HashMap<String, String>) source;
      for (Map.Entry<String, String> entry : converted.entrySet()) {
        dest.put(entry.getKey(), new StringByteIterator(entry.getValue()));
      }
    }
  }

  /**
   * Encode the object for spongebase storage.
   *
   * @param source the source value.
   * @return the storable object.
   */
  private Object encode(final HashMap<String, ByteIterator> source) {
    HashMap<String, String> stringMap = StringByteIterator.getStringMap(source);
    if (!useJson) {
      return stringMap;
    }

    ObjectNode node = JSON_MAPPER.createObjectNode();
    for (Map.Entry<String, String> pair : stringMap.entrySet()) {
      node.put(pair.getKey(), pair.getValue());
    }
    JsonFactory jsonFactory = new JsonFactory();
    Writer writer = new StringWriter();
    try {
      JsonGenerator jsonGenerator = jsonFactory.createGenerator(writer);
      JSON_MAPPER.writeTree(jsonGenerator, node);
    } catch (Exception e) {
      throw new RuntimeException("Could not encode JSON value");
    }
    return writer.toString();
  }
}
