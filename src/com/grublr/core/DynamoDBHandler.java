package com.grublr.core;

import com.amazonaws.auth.InstanceProfileCredentialsProvider;
import com.amazonaws.services.dynamodbv2.AmazonDynamoDBClient;
import com.amazonaws.services.dynamodbv2.model.AttributeValue;
import com.amazonaws.services.dynamodbv2.model.ComparisonOperator;
import com.amazonaws.services.dynamodbv2.model.Condition;
import com.amazonaws.services.dynamodbv2.model.DeleteItemRequest;
import com.amazonaws.services.dynamodbv2.model.PutItemRequest;
import com.amazonaws.services.dynamodbv2.model.QueryRequest;
import com.amazonaws.services.dynamodbv2.model.QueryResult;
import com.amazonaws.util.json.JSONException;
import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.databind.JsonNode;
import com.grublr.geo.GeoDataManager;
import com.grublr.geo.GeoDataManagerConfiguration;
import com.grublr.geo.model.GeoPoint;
import com.grublr.geo.model.GeoQueryResult;
import com.grublr.geo.model.PutPointRequest;
import com.grublr.geo.model.QueryRadiusRequest;
import com.grublr.geo.model.QueryRadiusResult;
import com.grublr.util.Constants;
import com.grublr.util.Utils;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Created by adi on 9/3/15.
 */
public class DynamoDBHandler implements DataStoreHandler {

    private DynamoDBHandler() {

    }

    private static DynamoDBHandler instance;
    private static final Logger log = Logger.getLogger(DynamoDBHandler.class.getName());

    private static final AmazonDynamoDBClient dbClient = new AmazonDynamoDBClient(new InstanceProfileCredentialsProvider());
    private static final GeoDataManagerConfiguration config = new GeoDataManagerConfiguration(dbClient, Constants.DYNAMO_DB_IMAGE_METADATA_TABLE);
    private static final GeoDataManager geoDataManager = new GeoDataManager(config);

    static {
        config.withRangeKeyAttributeName(Constants.UNIQUE_NAME);
    }

    public static final DynamoDBHandler getInstance() {
        if (instance == null) {
            instance = new DynamoDBHandler();
        }
        return instance;
    }

    public boolean addUser(String userName, String password, String salt) throws Exception {
        try {
            Map<String, AttributeValue> m = new HashMap<>();
            m.put(Constants.USERNAME_COL, new AttributeValue(userName));
            m.put(Constants.PASSWORD_COL, new AttributeValue(password));
            m.put(Constants.SALT_COL, new AttributeValue(salt));
            PutItemRequest putItemRequest = new PutItemRequest(Constants.DYNAMO_DB_IMAGE_USERS_TABLE, m);
            dbClient.putItem(putItemRequest);
            return true;
        } catch (Exception e) {
            throw e;
        }
    }

    @Override
    public void writeMetaData(String associatedImageName, JsonNode jsonData) throws IOException, JSONException {
        long begin = System.currentTimeMillis();
        if (log.isLoggable(Level.INFO)) log.info("Storing metadata");
        try {
            putPoint(associatedImageName, jsonData);
            if (log.isLoggable(Level.INFO))
                log.info("Stored metadata and time taken: " + (System.currentTimeMillis() - begin));
        } catch (Exception e) {
            throw e;
        }
    }

    @Override
    public List<JsonNode> readMetaData(JsonNode location) throws IOException, JSONException {
        long begin = System.currentTimeMillis();
        if (log.isLoggable(Level.INFO)) log.info("Getting posts");
        try {
            //GeoSpatial radius search
            List<JsonNode> posts = getPostsInRadius(location);
            if (log.isLoggable(Level.INFO))
                log.info("Got metadata and time taken: " + (System.currentTimeMillis() - begin));
            return posts;
        } catch (Exception e) {
            throw e;
        }
    }

    @Override
    public void editMetaData(JsonNode entityObj) throws Exception {
        if (log.isLoggable(Level.INFO)) log.info("Editing metadata");
        String uniqueName = entityObj.get(Constants.UNIQUE_NAME).asText();
        try {
            putPoint(uniqueName, entityObj);
            if (log.isLoggable(Level.INFO)) log.info("Edited metadata");
        } catch (Exception e) {
            throw e;
        }
    }

    private void putPoint(String associatedImageName, JsonNode node) throws IOException, JSONException {
        GeoPoint geoPoint = new GeoPoint(node.get(Constants.LATITUDE).doubleValue(), node.get(Constants.LONGITUDE).doubleValue());
        AttributeValue rangeKeyAttributeValue = new AttributeValue().withS(associatedImageName);
        PutPointRequest putPointRequest = new PutPointRequest(geoPoint, rangeKeyAttributeValue);
        Iterator<Map.Entry<String, JsonNode>> iter = node.fields();
        while (iter.hasNext()) {
            Map.Entry<String, JsonNode> entry = iter.next();
            String key = entry.getKey();
            if(key.equals(Constants.LATITUDE) || key.equals(Constants.LONGITUDE)) {
                continue;
            }
            AttributeValue attributeValue = new AttributeValue().withS(entry.getValue().asText());
            putPointRequest.getPutItemRequest().addItemEntry(key, attributeValue);
        }
        geoDataManager.putPoint(putPointRequest);
    }

    private List<JsonNode> getPostsInRadius(JsonNode node) throws IOException, JSONException {
        GeoPoint centerPoint = new GeoPoint(node.get(Constants.LATITUDE).doubleValue(), node.get(Constants.LONGITUDE).doubleValue());
        double radiusInMeter = node.get(Constants.SEARCH_RADIUS_IN_METERS).doubleValue();
        QueryRadiusRequest queryRadiusRequest = new QueryRadiusRequest(centerPoint, radiusInMeter);
        QueryRadiusResult result = geoDataManager.queryRadius(queryRadiusRequest);
        return resultToNodes(result);
    }

    private List<JsonNode> resultToNodes(GeoQueryResult geoQueryResult) throws JsonParseException, IOException {
        List<JsonNode> nodes = new ArrayList<>();
        for(Map<String, AttributeValue> item : geoQueryResult.getItem()) {
            String geoJsonString = item.get(config.getGeoJsonAttributeName()).getS();
            JsonNode jsonNode = Utils.stringToJson(geoJsonString);

            double latitude = jsonNode.get("coordinates").get(0).doubleValue();
            double longitude = jsonNode.get("coordinates").get(1).doubleValue();

            Map<String, Object> resultMap = new HashMap<>();
            resultMap.put(Constants.LATITUDE, Double.toString(latitude));
            resultMap.put(Constants.LONGITUDE, Double.toString(longitude));
            resultMap.put(Constants.UNIQUE_NAME, item.get(Constants.UNIQUE_NAME).getS());
            resultMap.put(Constants.NAME, item.get(Constants.NAME).getS());
            resultMap.put(Constants.DESCRIPTION, item.get(Constants.DESCRIPTION).getS());

            JsonNode node = Utils.mapToJson(resultMap);
            nodes.add(node);
        }
        return nodes;
    }

    @Override
    public void deleteData(String uniqueName) throws Exception {
        if (log.isLoggable(Level.INFO)) log.info("Deleting post");
        try {
            Map<String, AttributeValue> key = new HashMap<>(1);
            key.put(Constants.UNIQUE_NAME, new AttributeValue(uniqueName));
            DeleteItemRequest deleteItemRequest = new DeleteItemRequest(Constants.DYNAMO_DB_IMAGE_METADATA_TABLE, key);
            dbClient.deleteItem(deleteItemRequest);
            if (log.isLoggable(Level.INFO)) log.info("Deleted post");
        } catch (Exception e) {
            throw e;
        }
    }

    public boolean checkUserNameExists(String userName) {
        try {
            Map<String, Condition> queryFilter = new HashMap<>(1);
            Condition condition = new Condition()
                    .withComparisonOperator(ComparisonOperator.EQ)
                    .withAttributeValueList(new AttributeValue(userName));
            queryFilter.put(Constants.USERNAME_COL, condition);
            QueryRequest query = new QueryRequest(Constants.DYNAMO_DB_IMAGE_USERS_TABLE).withQueryFilter(queryFilter);
            QueryResult result = dbClient.query(query);
            if (result.getCount() > 0) {
                return true;
            }
        } catch (Exception e) {
            throw e;
        }
        return false;
    }

    public boolean getUser(String userName) {
        try {
            Map<String, Condition> queryFilter = new HashMap<>(1);
            Condition condition = new Condition()
                    .withComparisonOperator(ComparisonOperator.EQ)
                    .withAttributeValueList(new AttributeValue(userName));
            queryFilter.put(Constants.USERNAME_COL, condition);
            QueryRequest query = new QueryRequest(Constants.DYNAMO_DB_IMAGE_USERS_TABLE).withQueryFilter(queryFilter);
            QueryResult result = dbClient.query(query);
            Map<String, String> retMap = new HashMap<>();
            if (result.getCount() > 0) {
                List<Map<String, AttributeValue>> list = result.getItems();
                for (Map<String, AttributeValue> m : list) {
                    retMap.put(m.en)
                }
            }
        } catch (Exception e) {
            throw e;
        }
        return false;
    }

    /*private void queryRectangle(JSONObject requestObject, PrintWriter out) throws IOException, JSONException {
        GeoPoint minPoint = new GeoPoint(requestObject.getDouble("minLat"), requestObject.getDouble("minLng"));
        GeoPoint maxPoint = new GeoPoint(requestObject.getDouble("maxLat"), requestObject.getDouble("maxLng"));

        List<String> attributesToGet = new ArrayList<String>();
        attributesToGet.add(config.getRangeKeyAttributeName());
        attributesToGet.add(config.getGeoJsonAttributeName());
        attributesToGet.add("schoolName");

        QueryRectangleRequest queryRectangleRequest = new QueryRectangleRequest(minPoint, maxPoint);
        queryRectangleRequest.getQueryRequest().setAttributesToGet(attributesToGet);
        QueryRectangleResult queryRectangleResult = geoDataManager.queryRectangle(queryRectangleRequest);

        resultToNodes(queryRectangleResult, out);
    } */

}
