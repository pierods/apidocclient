/**
    This program is free software: you can redistribute it and/or modify
    it under the terms of the GNU General Public License as published by
    the Free Software Foundation, either version 3 of the License, or
    (at your option) any later version.

    This program is distributed in the hope that it will be useful,
    but WITHOUT ANY WARRANTY; without even the implied warranty of
    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
    GNU General Public License for more details.

    You should have received a copy of the GNU General Public License
    along with this program.  If not, see <http://www.gnu.org/licenses/>.

 */

package com.github.pierods.apidocclient;

import com.fasterxml.jackson.databind.ObjectMapper;

import org.jboss.resteasy.client.jaxrs.ResteasyClientBuilder;
import org.jboss.resteasy.client.jaxrs.ResteasyWebTarget;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;

import javax.ws.rs.client.Client;
import javax.ws.rs.client.Entity;
import javax.ws.rs.client.Invocation;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import joptsimple.OptionParser;
import joptsimple.OptionSet;

/**
 * A client for apidoc.me. It operates with a token that must be generated on the web site.
 * Key concepts are: The Organization, the Application Name, the Application Key, the Application version.
 * As an example:
 * The organization Acme has an orgkey of acme. http://apidoc.me/acme
 * The application (application means service here) "Acme Service" has an application key of acmeservice. The name "Acme Service" is shown in the app list on the site.
 * When navigating to /orgkey/appkey ( http://apidoc.me/acme/acmeservice/) one can browse through version of the service
 * http://apidoc.me/acme/acmeservice/0.0.1
 * When uploading a new version of an app, one must use the appkey of an existing app in the json file
 *
 *  orgkey: the key of the organization. This comes first in the url. Examples: http://apidoc.me/acme
 *
 */
public class ApidocClient {

    public class ApidocResponse {

        public ApidocResponse(int httpResponseCode, String reason, String message) {
            this.httpResponseCode = httpResponseCode;
            this.reason = reason;
            this.message = message;
        }

        public int httpResponseCode;
        public String reason;
        public String message;

        public String toString() {
            return "httpresponsecode=" + httpResponseCode + "reason=" + reason + "message=" + message;
        }
    }

    public enum Visibility {
        PUBLIC("public"),
        USER("user"),
        ORGANIZATION("organization");

        private final String name;

        Visibility(String name) {
            this.name = name;
        }

        public String toString() {
            return this.name;
        }
    }

    private String encryptToken(String token) {
        return encoder.encodeToString(token.replaceAll("\\s+", "").getBytes());
    }

    /**
     * Creates an application on apidoc.me . This call behaves as an INSERT and not an UPSERT
     *
     * @param orgKey 
     * @return 200 success, 409 app already exists
     */
    public ApidocResponse createApp(String token, String orgKey, String appName, String appKey, String description, Visibility visibility) throws IOException {

        class Application {
            public String name;
            public String key;
            public String description;
            public String visibility;
        }

        Application application = new Application();

        application.name = appName;
        application.key = appKey;
        application.description = description;
        application.visibility = visibility.name;

        String createAppString = mapper.writeValueAsString(application);

        Client client = new ResteasyClientBuilder().build();

        ResteasyWebTarget target = (ResteasyWebTarget) client.target("http://api.apidoc.me/" + orgKey);

        Invocation.Builder invocationBuilder = target.request("text/plain").header("Content-Type", "application/json").header("Authorization", "Basic " + encryptToken(token));

        Entity<String> someEntity = Entity.entity(createAppString, MediaType.APPLICATION_JSON);

        Invocation invocation = invocationBuilder.buildPost(someEntity);

        Response httpResponse = invocation.invoke();

        int responseStatus = httpResponse.getStatus();
        String reasonPhrase = httpResponse.getStatusInfo().getReasonPhrase();

        String responseString = httpResponse.readEntity(String.class);

        httpResponse.close();

        return new ApidocResponse(responseStatus, reasonPhrase, responseString);
    }

    /**
     * Deletes an application and its versions.
     *
     * @param orgKey 
     * @return 204 success
     */
    public ApidocResponse deleteApp(String token, String orgKey, String appKey) throws IOException {

        Client client = new ResteasyClientBuilder().build();

        ResteasyWebTarget target = (ResteasyWebTarget) client.target("http://api.apidoc.me/" + orgKey + "/" + appKey);

        Invocation.Builder invocationBuilder = target.request("text/plain").header("Content-Type", "application/json").header("Authorization", "Basic " + encryptToken(token));

        Invocation invocation = invocationBuilder.buildDelete();

        Response httpResponse = invocation.invoke();

        int responseStatus = httpResponse.getStatus();
        String reasonPhrase = httpResponse.getStatusInfo().getReasonPhrase();

        String responseString = httpResponse.readEntity(String.class);

        httpResponse.close();

        return new ApidocResponse(responseStatus, reasonPhrase, responseString);
    }

    /**
     * Creates the specified version of an app. This method has an UPSERT behavior.
     *
     * @param token          
     * @param appKey     must coincide with "name" key in json data
     * @param version        obeys to semver for REST
     * @param newServiceJson obeys to apidoc json spec
     * @param visibility     one of PUBLIC/USER/ORGANIZATION
     * @return 200 for OK.
     */
    public ApidocResponse createAppVersion(String token, String orgKey, String appKey, String version, String newServiceJson, Visibility visibility) throws IOException {

        class VersionForm {
            public Map<String, String> original_form = new HashMap();
            public String visibility;
        }

        VersionForm vf = new VersionForm();

        vf.visibility = visibility.name;

        vf.original_form.put("data", newServiceJson);

        String encodedData = mapper.writeValueAsString(vf);

        Entity<String> versionUpsertEntity = Entity.entity(encodedData, MediaType.APPLICATION_JSON);

        Client client = new ResteasyClientBuilder().build();

        ResteasyWebTarget target = (ResteasyWebTarget) client.target("http://api.apidoc.me/" + orgKey + "/" + appKey + "/" + version);

        Invocation.Builder invocationBuilder = target.request("text/plain").header("Content-Type", "application/json").header("Authorization", "Basic " + encryptToken(token));

        Invocation invocation = invocationBuilder.buildPut(versionUpsertEntity);

        Response httpResponse = invocation.invoke();

        int responseStatus = httpResponse.getStatus();
        String reasonPhrase = httpResponse.getStatusInfo().getReasonPhrase();

        String responseString = httpResponse.readEntity(String.class);

        httpResponse.close();

        return new ApidocResponse(responseStatus, reasonPhrase, responseString);
    }

    private Base64.Encoder encoder = Base64.getEncoder();
    // cannot use gson - it will not process local classes
    private ObjectMapper mapper = new ObjectMapper();

    public static void main(String[] args) throws IOException {

        OptionParser optionParser = new OptionParser("h");
        optionParser.accepts("create");
        optionParser.accepts("delete");
        optionParser.accepts("createversion");
        optionParser.accepts("token").withRequiredArg().required();
        optionParser.accepts("orgkey").withRequiredArg().required();
        optionParser.accepts("appkey").withRequiredArg().required();
        optionParser.accepts("appname").requiredIf("create").withRequiredArg();
        optionParser.accepts("description").requiredIf("create").withRequiredArg();
        optionParser.accepts("visibility").requiredIf("create").requiredIf("createversion").withRequiredArg();
        optionParser.accepts("version").requiredIf("createversion").withRequiredArg();
        optionParser.accepts("help").forHelp();

        OptionSet optionSet = optionParser.parse(args);

        if (optionSet.has("help")) {
            System.out.println("Options: create delete createversion token orgkey appkey appname description visibility version");
        }

        ApidocClient instance = new ApidocClient();

        String token = (String) optionSet.valueOf("token");
        String orgKey = (String) optionSet.valueOf("orgkey");
        String appKey =  (String) optionSet.valueOf("appkey");

        if (optionSet.has("delete")) {
            System.out.println(instance.deleteApp(token, orgKey, appKey));
            return;
        }

        if (! (optionSet.has("create") || optionSet.has("delete") || optionSet.has("createversion"))) {
            System.out.println("Must specify one of create, delete, createversion");
            System.exit(1);
        }
        String visibility = ((String) optionSet.valueOf("visibility")).toLowerCase();

        if (!(visibility.equals(Visibility.ORGANIZATION.name) || visibility.equals(Visibility.PUBLIC.name) || visibility.equals(Visibility.USER.name))) {
            System.out.println("visibility must be one of organization, public, user");
            System.exit(-1);
        }

        if (optionSet.has("create")) {

            String appName = (String) optionSet.valueOf("appname");
            String description =  (String) optionSet.valueOf("description");

            System.out.println(instance.createApp(token, orgKey, appName, appKey, description, Visibility.valueOf(visibility.toUpperCase())));
            return;
        }

        if (optionSet.has("createversion")) {

            String version = (String) optionSet.valueOf("version");

            BufferedReader br = new BufferedReader(new InputStreamReader(System.in));

            String data  = br.lines().collect(Collectors.joining());

            System.out.println(instance.createAppVersion(token, orgKey, appKey, version, data, Visibility.valueOf(visibility.toUpperCase())));
        }
    }
}