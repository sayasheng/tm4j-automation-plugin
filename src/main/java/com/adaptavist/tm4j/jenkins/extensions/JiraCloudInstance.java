package com.adaptavist.tm4j.jenkins.extensions;

import com.adaptavist.tm4j.jenkins.exception.InvalidJwtException;
import com.mashape.unirest.http.HttpResponse;
import com.mashape.unirest.http.JsonNode;
import com.mashape.unirest.http.Unirest;
import com.mashape.unirest.http.exceptions.UnirestException;
import com.nimbusds.jwt.JWTParser;
import hudson.util.Secret;
import net.minidev.json.JSONObject;
import org.apache.http.client.HttpClient;
import org.apache.http.impl.client.HttpClientBuilder;

import java.io.File;
import java.text.MessageFormat;
import java.text.ParseException;

public class JiraCloudInstance implements Instance {

    private static final String CUCUMBER_ENDPOINT = "{0}/tm4j/v2/automations/executions/cucumber";
    private static final String CUSTOM_FORMAT_ENDPOINT = "{0}/tm4j/v2/automations/executions/custom";
    private static final String FEATURE_FILES_ENDPOINT = "{0}/tm4j/v2/automations/testcases";
    private static final String TM4J_HEALTH_CHECK = "{0}/tm4j/v2/healthcheck";
    private static final String TM4J_API_BASE_URL = "https://api.adaptavist.io";
    private static final String CREATE_TEST_CYCLE_FOLDER_ENDPOINT = "{0}/tm4j/v2/folders";
    private static final String GET_TEST_CYCLE_ENDPOINT = "{0}/tm4j/v2/testcycles";
    private static final String UPDATE_TEST_CYCLE_ENDPOINT = "{0}/tm4j/v2/testcycles";
    
    private Secret jwt;
    private String name;

    public JiraCloudInstance() {

    }

    public JiraCloudInstance(Secret jwt) {
        this.jwt = jwt;
        this.name = getBaseUrl();
    }

    @Override
    public Boolean cloud() {
        return true;
    }

    @Override
    public String name() {
        return this.name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getCloudAddress() {
        return this.name;
    }

    @Override
    public Boolean isValidCredentials() {
        try {
            String url = MessageFormat.format(TM4J_HEALTH_CHECK, TM4J_API_BASE_URL);
            HttpClient httpClient = HttpClientBuilder.create().disableCookieManagement().build();
            Unirest.setHttpClient(httpClient);
            HttpResponse<String> response = Unirest.get(url)
                    .header("Authorization", "Bearer " + getDecryptedJwt())
                    .asString();
            return response.getStatus() == 200;
        } catch (UnirestException e) {
            e.printStackTrace();
        }
        return false;
    }

    @Override
    public HttpResponse<JsonNode> publishCucumberFormatBuildResult(String projectKey, Boolean autoCreateTestCases, File zip) throws UnirestException {
        String url = MessageFormat.format(CUCUMBER_ENDPOINT, TM4J_API_BASE_URL);
        HttpClient httpClient = HttpClientBuilder.create().disableCookieManagement().build();
        Unirest.setHttpClient(httpClient);
        return exportResultsFile(projectKey, autoCreateTestCases, zip, url);
    }

    @Override
    public HttpResponse<JsonNode> publishCustomFormatBuildResult(String projectKey, Boolean autoCreateTestCases, File zip) throws UnirestException {
        String url = MessageFormat.format(CUSTOM_FORMAT_ENDPOINT, TM4J_API_BASE_URL);
        HttpClient httpClient = HttpClientBuilder.create().disableCookieManagement().build();
        Unirest.setHttpClient(httpClient);
        return exportResultsFile(projectKey, autoCreateTestCases, zip, url);
    }

    @Override
    public HttpResponse<String> downloadFeatureFile(String projectKey) throws UnirestException {
        String url = MessageFormat.format(FEATURE_FILES_ENDPOINT, TM4J_API_BASE_URL);
        HttpClient httpClient = HttpClientBuilder.create().disableCookieManagement().build();
        Unirest.setHttpClient(httpClient);

        return Unirest.get(url)
                .header("Authorization", "Bearer " + getDecryptedJwt())
                .header("Accept", "application/zip")
                .queryString("projectKey", projectKey)
                .asString();
    }

    @Override
    public HttpResponse<JsonNode> createTestCycleFolder(String projectKey, String testCycleFolder) throws UnirestException {
    	String url = MessageFormat.format(CREATE_TEST_CYCLE_FOLDER_ENDPOINT, TM4J_API_BASE_URL);
    	HttpClient httpClient = HttpClientBuilder.create().disableCookieManagement().build();
    	Unirest.setHttpClient(httpClient);
    	String json = "{"+
    	                        "\"parentId\"" +":"+ "null" +"," +
    			                "\"name\"" +":" +"\""+testCycleFolder+"\"" + "," +
    			                "\"projectKey\"" + ":" + "\"" + projectKey +"\"" + "," +
    			                "\"folderType\"" + ":" + "\"" + "TEST_CYCLE" +"\"" +
    			              "}" ;
    	
    	return Unirest.post(url)
    			.header("Authorization", "Bearer " + getDecryptedJwt())
    			.header("Content-Type", "application/json")
    			.body(json)
    			.asJson();
    }
    
    @Override
    public HttpResponse<JsonNode> getTestCycle(String testCycleKey) throws UnirestException{
    	String url = MessageFormat.format(GET_TEST_CYCLE_ENDPOINT, TM4J_API_BASE_URL) +"/" +  testCycleKey;
        HttpClient httpClient = HttpClientBuilder.create().disableCookieManagement().build();
        Unirest.setHttpClient(httpClient);
        return Unirest.get(url)
                .header("Authorization", "Bearer " + getDecryptedJwt())
                .asJson();
    }
    
    @Override
    public HttpResponse<JsonNode> updateTestCycle(String testCycleKey, JsonNode json) throws UnirestException{
    	String url = MessageFormat.format(UPDATE_TEST_CYCLE_ENDPOINT, TM4J_API_BASE_URL) +"/" +  testCycleKey;
        HttpClient httpClient = HttpClientBuilder.create().disableCookieManagement().build();
        Unirest.setHttpClient(httpClient);
        return Unirest.put(url)
        		.header("Authorization", "Bearer " + getDecryptedJwt())
        		.header("Content-Type", "application/json")
        		.body(json.toString())
        		.asJson();
    }
    
    
    public Secret getJwt() {
        return jwt;
    }

    public void setJwt(Secret jwt) {
        this.jwt = jwt;
        this.name = getBaseUrl();
    }

    public Boolean getCloud() {
        return true;
    }

    private String getBaseUrl() {
        try {
            Object context = JWTParser.parse(getDecryptedJwt()).getJWTClaimsSet().getClaim("context");
            if (context instanceof JSONObject) {
                return (String) ((JSONObject) context).get("baseUrl");
            }

            return null;

        } catch (ParseException e) {
            throw new InvalidJwtException(e);
        }
    }

    private HttpResponse<JsonNode> exportResultsFile(String projectKey, Boolean autoCreateTestCases, File zip, String url) throws UnirestException {
        return Unirest.post(url)
                .header("Authorization", "Bearer " + getDecryptedJwt())
                .queryString("autoCreateTestCases", autoCreateTestCases)
                .queryString("projectKey", projectKey)
                .field("file", zip)
                .asJson();
    }

    private String getDecryptedJwt() {
        return jwt.getPlainText();
    }
}
