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

import org.junit.BeforeClass;
import org.junit.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;

import static org.junit.Assert.*;

/**
 * Created by piero on 4/20/16.
 */
public class ApidocClientTest {

    static String newServiceJson;
    static String token;

    @BeforeClass
    public static void setUp() throws IOException {
        newServiceJson = new String(Files.readAllBytes(Paths.get(new java.io.File(".").getCanonicalPath(), "src/test/resources/hello.apidoc")));
        token = new String(Files.readAllBytes(Paths.get(new java.io.File(".").getCanonicalPath(), "src/test/resources/token.txt")));
    }


    @Test
    public void createApp() throws Exception {

        ApidocClient client = new ApidocClient();

        ApidocClient.ApidocResponse response = new ApidocClient().createApp(token, "acme", "Test Service", "testservice", "test service", ApidocClient.Visibility.ORGANIZATION);

        assertEquals(200, response.httpResponseCode);

        response = client.deleteApp(token, "acme", "acmeservice");

        assertEquals(204, response.httpResponseCode);

    }

    @Test
    public void createAppVersion() throws Exception {

        ApidocClient client = new ApidocClient();

        ApidocClient.ApidocResponse response = new ApidocClient().createApp(token, "acme", "Test Service", "testservice", "test service", ApidocClient.Visibility.ORGANIZATION);

        response = new ApidocClient().createAppVersion(token, "acme", "acmeservice", "0.0.1", newServiceJson, ApidocClient.Visibility.ORGANIZATION);

        assertEquals(200, response.httpResponseCode);

        response = client.deleteApp(token, "acme", "acmeservice");
    }

}
