/*
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package cool.pandora.ldpclient;

import static java.time.Instant.ofEpochMilli;
import static java.time.ZoneOffset.UTC;
import static java.time.format.DateTimeFormatter.RFC_1123_DATE_TIME;
import static java.util.Collections.synchronizedList;
import static java.util.Objects.nonNull;
import static java.util.Objects.requireNonNull;
import static jdk.incubator.http.HttpClient.Version.HTTP_2;
import static jdk.incubator.http.HttpResponse.BodyHandler.asByteArray;
import static jdk.incubator.http.HttpResponse.BodyHandler.asFile;
import static jdk.incubator.http.HttpResponse.BodyHandler.asString;
import static org.apache.jena.riot.WebContent.contentTypeTurtle;
import static org.slf4j.LoggerFactory.getLogger;

import jdk.incubator.http.HttpClient;
import jdk.incubator.http.HttpRequest;
import jdk.incubator.http.HttpResponse;
import org.apache.commons.rdf.api.IRI;
import org.apache.jena.riot.WebContent;
import org.slf4j.Logger;
import org.trellisldp.vocabulary.DC;
import org.trellisldp.vocabulary.LDP;
import org.trellisldp.vocabulary.Trellis;
import javax.net.ssl.SSLContext;
import javax.ws.rs.core.HttpHeaders;
import java.io.InputStream;
import java.net.URI;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;

/**
 * LdpClientImpl.
 *
 * @author christopher-johnson
 */
public class LdpClientImpl implements LdpClient {
    private static final Logger log = getLogger(LdpClientImpl.class);
    private static final String NON_NULL_IDENTIFIER = "Identifier may not be null!";
    private static SSLContext sslContext;
    private static HttpClient client = null;

    private LdpClientImpl(final HttpClient client) {
        requireNonNull(client, "HTTP client may not be null!");
        LdpClientImpl.client = client;
    }

    /**
     *
     */
    public LdpClientImpl() {
        this(getClient());
    }

    private static HttpClient getClient() {
        final ExecutorService exec = Executors.newCachedThreadPool();
        return HttpClient.newBuilder()
                         .executor(exec)
                         // .sslContext(sslContext)
                         .version(HTTP_2)
                         .build();
    }

    private static String buildLDFQuery(final String subject, final String predicate, final String object) {
        String sq = "";
        String pq = "";
        String oq = "";

        if (nonNull(subject)) {
            sq = "subject=" + subject;
        }

        if (nonNull(predicate) && nonNull(subject)) {
            pq = "&predicate=" + predicate;
        } else if (nonNull(predicate)) {
            pq = "predicate=" + predicate;
        }

        if (nonNull(predicate) && nonNull(object) || (nonNull(subject) && nonNull(object))) {
            oq = "&object=" + object;
        } else if (nonNull(object)) {
            oq = "object=" + object;
        }

        return "?" + sq + pq + oq;
    }

    private synchronized String[] buildHeaderEntryList(final Map<String, String> metadata) {
        final List<String> h = synchronizedList(new ArrayList<>());
        metadata.forEach((key, value) -> {
            h.add(key);
            h.add(value);
        });
        return h.toArray(new String[h.size()]);
    }

    @Override
    public Map<String, List<String>> head(final IRI identifier) throws LdpClientException {
        try {
            requireNonNull(identifier, NON_NULL_IDENTIFIER);
            final URI uri = new URI(identifier.getIRIString());
            final HttpRequest req = HttpRequest.newBuilder(uri)
                                               .method("HEAD", HttpRequest.noBody())
                                               .build();
            final HttpResponse<String> response = client.send(req, asString());
            log.info(String.valueOf(response.version()) + " HEAD request to {} returned {}", identifier, String
                    .valueOf(response.statusCode()));
            return response.headers()
                           .map();
        } catch (Exception ex) {
            throw new LdpClientException(ex.toString(), ex.getCause());
        }
    }

    @Override
    public String getJson(final IRI identifier) throws LdpClientException {
        try {
            requireNonNull(identifier, NON_NULL_IDENTIFIER);
            final URI uri = new URI(identifier.getIRIString());
            final String[] headers = new String[]{HttpHeaders.ACCEPT, WebContent.contentTypeJSONLD};
            final HttpRequest req = HttpRequest.newBuilder(uri)
                                               .headers(headers)
                                               .GET()
                                               .build();
            final HttpResponse<String> response = client.send(req, asString());
            log.info(String.valueOf(response.version()) + " GET request to {} returned {}", identifier, String
                    .valueOf(response.statusCode()));
            return response.body();
        } catch (Exception ex) {
            throw new LdpClientException(ex.toString(), ex.getCause());
        }
    }

    @Override
    public String getDefaultType(final IRI identifier) throws LdpClientException {
        try {
            requireNonNull(identifier, NON_NULL_IDENTIFIER);
            final URI uri = new URI(identifier.getIRIString());
            final HttpRequest req = HttpRequest.newBuilder(uri)
                                               .GET()
                                               .build();
            final HttpResponse<String> response = client.send(req, asString());
            log.info(String.valueOf(response.version()) + " GET request to {} returned {}", identifier, String
                    .valueOf(response.statusCode()));
            return response.body();
        } catch (Exception ex) {
            throw new LdpClientException(ex.toString(), ex.getCause());
        }
    }

    @Override
    public String getWithContentType(final IRI identifier, final String contentType) throws LdpClientException {
        try {
            requireNonNull(identifier, NON_NULL_IDENTIFIER);
            final URI uri = new URI(identifier.getIRIString());
            final HttpRequest req = HttpRequest.newBuilder(uri)
                                               .headers(HttpHeaders.ACCEPT, contentType)
                                               .GET()
                                               .build();
            final HttpResponse<String> response = client.send(req, asString());
            log.info(String.valueOf(response.version()) + " GET request to {} returned {}", identifier, String
                    .valueOf(response.statusCode()));
            return response.body();
        } catch (Exception ex) {
            throw new LdpClientException(ex.toString(), ex.getCause());
        }
    }

    @Override
    public Map<String, List<String>> getAcceptDatetime(final IRI identifier, final String timestamp) throws
            LdpClientException {
        try {
            requireNonNull(identifier, NON_NULL_IDENTIFIER);
            final URI uri = new URI(identifier.getIRIString());
            final String datetime = RFC_1123_DATE_TIME.withZone(UTC)
                                                      .format(ofEpochMilli(Long.parseLong(timestamp)));
            final HttpRequest req = HttpRequest.newBuilder(uri)
                                               .headers("Accept-Datetime", datetime)
                                               .GET()
                                               .build();
            final HttpResponse<String> response = client.send(req, asString());
            log.info(String.valueOf(response.version()) + " GET request to {} returned {}", identifier, String
                    .valueOf(response.statusCode()));
            return response.headers()
                           .map();
        } catch (Exception ex) {
            throw new LdpClientException(ex.toString(), ex.getCause());
        }
    }

    @Override
    public String getTimeMapLinkDefaultFormat(final IRI identifier) throws LdpClientException {
        try {
            requireNonNull(identifier, NON_NULL_IDENTIFIER);
            final URI uri = new URI(identifier.getIRIString() + "?ext=timemap");
            final HttpRequest req = HttpRequest.newBuilder(uri)
                                               .GET()
                                               .build();
            final HttpResponse<String> response = client.send(req, asString());
            log.info(String.valueOf(response.version()) + " GET request to {} returned {}", identifier, String
                    .valueOf(response.statusCode()));
            return response.body();
        } catch (Exception ex) {
            throw new LdpClientException(ex.toString(), ex.getCause());
        }
    }

    @Override
    public String getTimeMapJsonProfile(final IRI identifier, final String profile) throws LdpClientException {
        try {
            requireNonNull(identifier, NON_NULL_IDENTIFIER);
            final URI uri = new URI(identifier.getIRIString() + "?ext=timemap");
            final String[] headers = new String[]{HttpHeaders.ACCEPT, WebContent.contentTypeJSONLD + "; " +
                    "profile=\"" + profile + "\""};
            final HttpRequest req = HttpRequest.newBuilder(uri)
                                               .headers(headers)
                                               .GET()
                                               .build();
            final HttpResponse<String> response = client.send(req, asString());
            log.info(String.valueOf(response.version()) + " GET request to {} returned {}", identifier +
                    "?ext=timemap", String.valueOf(response.statusCode()));
            return response.body();
        } catch (Exception ex) {
            throw new LdpClientException(ex.toString(), ex.getCause());
        }
    }

    @Override
    public String getVersionJson(final IRI identifier, final String profile, final String timestamp) throws
            LdpClientException {
        try {
            requireNonNull(identifier, NON_NULL_IDENTIFIER);
            final URI uri = new URI(identifier.getIRIString() + "?version=" + timestamp);
            final String[] headers = new String[]{HttpHeaders.ACCEPT, WebContent.contentTypeJSONLD + "; " +
                    "profile=\"" + profile + "\""};
            final HttpRequest req = HttpRequest.newBuilder(uri)
                                               .headers(headers)
                                               .GET()
                                               .build();
            final HttpResponse<String> response = client.send(req, asString());
            log.info(String.valueOf(response.version()) + " GET request to {} returned {}", identifier + "?version="
                    + timestamp, String.valueOf(response.statusCode()));
            return response.body();
        } catch (Exception ex) {
            throw new LdpClientException(ex.toString(), ex.getCause());
        }
    }

    @Override
    public Path getBinary(final IRI identifier, final Path file) throws LdpClientException {
        try {
            requireNonNull(identifier, NON_NULL_IDENTIFIER);
            final URI uri = new URI(identifier.getIRIString());
            final HttpRequest req = HttpRequest.newBuilder(uri)
                                               .GET()
                                               .build();
            final HttpResponse<Path> response = client.send(req, asFile(file));
            log.info(String.valueOf(response.version()) + " GET request to {} returned {}", identifier, String
                    .valueOf(response.statusCode()));
            return response.body();
        } catch (Exception ex) {
            throw new LdpClientException(ex.toString(), ex.getCause());
        }
    }

    @Override
    public byte[] getBinary(final IRI identifier) throws LdpClientException {
        try {
            requireNonNull(identifier, NON_NULL_IDENTIFIER);
            final URI uri = new URI(identifier.getIRIString());
            final HttpRequest req = HttpRequest.newBuilder(uri)
                                               .GET()
                                               .build();
            final HttpResponse<byte[]> response = client.send(req, asByteArray());
            log.info(String.valueOf(response.version()) + " GET request to {} returned {}", identifier, String
                    .valueOf(response.statusCode()));
            return response.body();
        } catch (Exception ex) {
            throw new LdpClientException(ex.toString(), ex.getCause());
        }
    }

    @Override
    public String getBinaryDigest(final IRI identifier, final String algorithm) throws LdpClientException {
        try {
            requireNonNull(identifier, NON_NULL_IDENTIFIER);
            final URI uri = new URI(identifier.getIRIString());
            final HttpRequest req = HttpRequest.newBuilder(uri)
                                               .headers("Want-Digest", algorithm)
                                               .method("HEAD", HttpRequest.noBody())
                                               .build();
            final HttpResponse<String> response = client.send(req, asString());
            log.info(String.valueOf(response.version()) + " GET request to {} returned {}", identifier, String
                    .valueOf(response.statusCode()));
            final List<List<String>> res = response.headers()
                                                   .map()
                                                   .entrySet()
                                                   .stream()
                                                   .filter(h -> h.getKey()
                                                                 .equals("digest"))
                                                   .map(Map.Entry::getValue)
                                                   .collect(Collectors.toList());
            return res.stream()
                      .flatMap(List::stream)
                      .collect(Collectors.toList())
                      .stream()
                      .findAny()
                      .orElse("");
        } catch (Exception ex) {
            throw new LdpClientException(ex.toString(), ex.getCause());
        }
    }

    @Override
    public Path getBinaryVersion(final IRI identifier, final Path file, final String timestamp) throws
            LdpClientException {
        try {
            requireNonNull(identifier, NON_NULL_IDENTIFIER);
            final URI uri = new URI(identifier.getIRIString() + "?version=" + timestamp);
            final HttpRequest req = HttpRequest.newBuilder(uri)
                                               .GET()
                                               .build();
            final HttpResponse<Path> response = client.send(req, asFile(file));
            log.info(String.valueOf(response.version()) + " GET request to {} returned {}", identifier + "?version="
                    + timestamp, String.valueOf(response.statusCode()));
            return response.body();
        } catch (Exception ex) {
            throw new LdpClientException(ex.toString(), ex.getCause());
        }
    }

    @Override
    public byte[] getBinaryVersion(final IRI identifier, final String timestamp) throws LdpClientException {
        try {
            requireNonNull(identifier, NON_NULL_IDENTIFIER);
            final URI uri = new URI(identifier.getIRIString() + "?version=" + timestamp);
            final HttpRequest req = HttpRequest.newBuilder(uri)
                                               .GET()
                                               .build();
            final HttpResponse<byte[]> response = client.send(req, asByteArray());
            log.info(String.valueOf(response.version()) + " GET request to {} returned {}", identifier + "?version="
                    + timestamp, String.valueOf(response.statusCode()));
            return response.body();
        } catch (Exception ex) {
            throw new LdpClientException(ex.toString(), ex.getCause());
        }
    }

    @Override
    public byte[] getRange(final IRI identifier, final String byterange) throws LdpClientException {
        try {
            requireNonNull(identifier, NON_NULL_IDENTIFIER);
            final URI uri = new URI(identifier.getIRIString());
            final HttpRequest req = HttpRequest.newBuilder(uri)
                                               .headers("Range", byterange)
                                               .GET()
                                               .build();
            final HttpResponse<byte[]> response = client.send(req, asByteArray());
            log.info(String.valueOf(response.version()));
            log.info(String.valueOf(response.statusCode()));
            return response.body();
        } catch (Exception ex) {
            throw new LdpClientException(ex.toString(), ex.getCause());
        }
    }

    @Override
    public String getPrefer(final IRI identifier, final String prefer) throws LdpClientException {
        try {
            requireNonNull(identifier, NON_NULL_IDENTIFIER);
            final URI uri = new URI(identifier.getIRIString());
            final HttpRequest req = HttpRequest.newBuilder(uri)
                                               .headers("Prefer", prefer)
                                               .GET()
                                               .build();
            final HttpResponse<String> response = client.send(req, asString());
            log.info(String.valueOf(response.version()) + " GET request to {} returned {}", identifier, String
                    .valueOf(response.statusCode()));
            return response.body();
        } catch (Exception ex) {
            throw new LdpClientException(ex.toString(), ex.getCause());
        }
    }

    @Override
    public String getPreferServerManaged(final IRI identifier) throws LdpClientException {
        try {
            requireNonNull(identifier, NON_NULL_IDENTIFIER);
            final URI uri = new URI(identifier.getIRIString());
            final String[] headers = new String[]{"Prefer", "return=representation; include=\"" + Trellis
                    .PreferServerManaged.getIRIString() + "\""};
            final HttpRequest req = HttpRequest.newBuilder(uri)
                                               .headers(headers)
                                               .GET()
                                               .build();
            final HttpResponse<String> response = client.send(req, asString());
            log.info(String.valueOf(response.version()) + " GET request to {} returned {}", identifier, String
                    .valueOf(response.statusCode()));
            return response.body();
        } catch (Exception ex) {
            throw new LdpClientException(ex.toString(), ex.getCause());
        }
    }

    @Override
    public String getPreferMinimal(final IRI identifier) throws LdpClientException {
        try {
            requireNonNull(identifier, NON_NULL_IDENTIFIER);
            final URI uri = new URI(identifier.getIRIString());
            final String[] headers = new String[]{"Prefer", "return=representation; include=\"" + LDP
                    .PreferMinimalContainer.getIRIString() + "\""};
            final HttpRequest req = HttpRequest.newBuilder(uri)
                                               .headers(headers)
                                               .GET()
                                               .build();
            final HttpResponse<String> response = client.send(req, asString());
            log.info(String.valueOf(response.version()) + " GET request to {} returned {}", identifier, String
                    .valueOf(response.statusCode()));
            return response.body();
        } catch (Exception ex) {
            throw new LdpClientException(ex.toString(), ex.getCause());
        }
    }

    @Override
    public String getJsonProfile(final IRI identifier, final String profile) throws LdpClientException {
        try {
            requireNonNull(identifier, NON_NULL_IDENTIFIER);
            final URI uri = new URI(identifier.getIRIString());
            final String[] headers = new String[]{HttpHeaders.ACCEPT, WebContent.contentTypeJSONLD + "; " +
                    "profile=\"" + profile + "\""};
            final HttpRequest req = HttpRequest.newBuilder(uri)
                                               .headers(headers)
                                               .GET()
                                               .build();
            final HttpResponse<String> response = client.send(req, asString());
            log.info(String.valueOf(response.version()) + " GET request to {} returned {}", identifier, String
                    .valueOf(response.statusCode()));
            return response.body();
        } catch (Exception ex) {
            throw new LdpClientException(ex.toString(), ex.getCause());
        }
    }

    @Override
    public String getJsonProfileLDF(final IRI identifier, final String profile, final String subject, final String
            predicate, final String object) throws LdpClientException {
        try {
            requireNonNull(identifier, NON_NULL_IDENTIFIER);
            final String q = buildLDFQuery(subject, predicate, object);
            final URI uri = new URI(identifier.getIRIString() + q);
            final String[] headers = new String[]{HttpHeaders.ACCEPT, WebContent.contentTypeJSONLD + "; " +
                    "profile=\"" + profile + "\""};
            final HttpRequest req = HttpRequest.newBuilder(uri)
                                               .headers(headers)
                                               .GET()
                                               .build();
            final HttpResponse<String> response = client.send(req, asString());
            log.info(String.valueOf(response.version()) + " GET request to {} returned {}", identifier, String
                    .valueOf(response.statusCode()));
            return response.body();
        } catch (Exception ex) {
            throw new LdpClientException(ex.toString(), ex.getCause());
        }
    }

    @Override
    public String getJsonLDF(final IRI identifier, final String subject, final String predicate, final String object)
            throws LdpClientException {
        try {
            requireNonNull(identifier, NON_NULL_IDENTIFIER);
            final String q = buildLDFQuery(subject, predicate, object);
            final URI uri = new URI(identifier.getIRIString() + q);
            final HttpRequest req = HttpRequest.newBuilder(uri)
                                               .headers(HttpHeaders.ACCEPT, WebContent.contentTypeJSONLD)
                                               .GET()
                                               .build();
            final HttpResponse<String> response = client.send(req, asString());
            log.info(String.valueOf(response.version()) + " GET request to {} returned {}", identifier + q, String
                    .valueOf(response.statusCode()));
            return response.body();
        } catch (Exception ex) {
            throw new LdpClientException(ex.toString(), ex.getCause());
        }

    }

    @Override
    public String getAcl(final IRI identifier, final String contentType) throws LdpClientException {
        try {
            requireNonNull(identifier, NON_NULL_IDENTIFIER);
            final URI uri = new URI(identifier.getIRIString() + "?ext=acl");
            final HttpRequest req = HttpRequest.newBuilder(uri)
                                               .headers(HttpHeaders.ACCEPT, contentType)
                                               .GET()
                                               .build();
            final HttpResponse<String> response = client.send(req, asString());
            log.info(String.valueOf(response.version()) + " GET request to {} returned {}", identifier + "?ext=acl",
                    String.valueOf(response.statusCode()));
            return response.body();
        } catch (Exception ex) {
            throw new LdpClientException(ex.toString(), ex.getCause());
        }
    }

    @Override
    public Map<String, List<String>> getCORS(final IRI identifier, final IRI origin) throws LdpClientException {
        try {
            requireNonNull(identifier, NON_NULL_IDENTIFIER);
            final URI uri = new URI(identifier.getIRIString());
            final String[] headers = new String[]{"Origin", origin.getIRIString(), "Access-Control-Request-Method",
                    "PUT", "Access-Control-Request-Headers", "Content-Type, Link"};
            final HttpRequest req = HttpRequest.newBuilder(uri)
                                               .headers(headers)
                                               .GET()
                                               .build();
            final HttpResponse<String> response = client.send(req, asString());
            log.info(String.valueOf(response.version()) + " GET request to {} returned {}", identifier, String
                    .valueOf(response.statusCode()));
            return response.headers()
                           .map();
        } catch (Exception ex) {
            throw new LdpClientException(ex.toString(), ex.getCause());
        }
    }

    @Override
    public Map<String, List<String>> getCORSSimple(final IRI identifier, final IRI origin) throws LdpClientException {
        try {
            requireNonNull(identifier, NON_NULL_IDENTIFIER);
            final URI uri = new URI(identifier.getIRIString());
            final String[] headers = new String[]{"Origin", origin.getIRIString(), "Access-Control-Request-Method",
                    "POST", "Access-Control-Request-Headers", "Accept"};
            final HttpRequest req = HttpRequest.newBuilder(uri)
                                               .headers(headers)
                                               .GET()
                                               .build();
            final HttpResponse<String> response = client.send(req, asString());
            log.info(String.valueOf(response.version()) + " GET request to {} returned {}", identifier, String
                    .valueOf(response.statusCode()));
            return response.headers()
                           .map();
        } catch (Exception ex) {
            throw new LdpClientException(ex.toString(), ex.getCause());
        }
    }

    @Override
    public String getWithMetadata(final IRI identifier, final Map<String, String> metadata) throws LdpClientException {
        try {
            requireNonNull(identifier, NON_NULL_IDENTIFIER);
            final URI uri = new URI(identifier.getIRIString());
            final String[] entries = buildHeaderEntryList(metadata);
            final HttpRequest req = HttpRequest.newBuilder(uri)
                                               .headers(entries)
                                               .GET()
                                               .build();
            final HttpResponse<String> response = client.send(req, asString());
            log.info(String.valueOf(response.version()) + " GET request to {} returned {}", identifier, String
                    .valueOf(response.statusCode()));
            return response.body();
        } catch (Exception ex) {
            throw new LdpClientException(ex.toString(), ex.getCause());
        }
    }

    @Override
    public byte[] getBytesWithMetadata(final IRI identifier, final Map<String, String> metadata) throws
            LdpClientException {
        try {
            requireNonNull(identifier, NON_NULL_IDENTIFIER);
            final URI uri = new URI(identifier.getIRIString());
            final String[] entries = buildHeaderEntryList(metadata);
            final HttpRequest req = HttpRequest.newBuilder(uri)
                                               .headers(entries)
                                               .GET()
                                               .build();
            final HttpResponse<byte[]> response = client.send(req, asByteArray());
            log.info(String.valueOf(response.version()) + " GET request to {} returned {}", identifier, String
                    .valueOf(response.statusCode()));
            return response.body();
        } catch (Exception ex) {
            throw new LdpClientException(ex.toString(), ex.getCause());
        }
    }

    @Override
    public Map<String, Map<String, List<String>>> getResponse(final IRI identifier, final Map<String, String>
            metadata) throws LdpClientException {
        try {
            requireNonNull(identifier, NON_NULL_IDENTIFIER);
            final URI uri = new URI(identifier.getIRIString());
            final String[] entries = buildHeaderEntryList(metadata);
            final HttpRequest req = HttpRequest.newBuilder(uri)
                                               .headers(entries)
                                               .GET()
                                               .build();
            final HttpResponse<String> response = client.send(req, asString());
            log.info(String.valueOf(response.version()) + " GET request to {} returned {}", identifier, String
                    .valueOf(response.statusCode()));
            final Map<String, Map<String, List<String>>> res = new HashMap<>();
            res.put(response.body(), response.headers()
                                             .map());
            return res;
        } catch (Exception ex) {
            throw new LdpClientException(ex.toString(), ex.getCause());
        }
    }

    @Override
    public Map<String, List<String>> options(final IRI identifier) throws LdpClientException {
        try {
            requireNonNull(identifier, NON_NULL_IDENTIFIER);
            final URI uri = new URI(identifier.getIRIString());
            final HttpRequest req = HttpRequest.newBuilder(uri)
                                               .method("OPTIONS", HttpRequest.noBody())
                                               .build();
            final HttpResponse<String> response = client.send(req, asString());
            log.info(String.valueOf(response.version()) + " OPTIONS request to {} returned {}", identifier, String
                    .valueOf(response.statusCode()));
            return response.headers()
                           .map();
        } catch (Exception ex) {
            throw new LdpClientException(ex.toString(), ex.getCause());
        }
    }

    @Override
    public void post(final IRI identifier, final InputStream stream, final String contentType) throws
            LdpClientException {
        try {
            requireNonNull(identifier, NON_NULL_IDENTIFIER);
            final URI uri = new URI(identifier.getIRIString());
            final HttpRequest req = HttpRequest.newBuilder(uri)
                                               .headers(HttpHeaders.CONTENT_TYPE, contentType)
                                               .POST(HttpRequest.BodyProcessor.fromInputStream(() -> stream))
                                               .build();
            final HttpResponse<String> response = client.send(req, asString());
            log.info("New Resource Location {}", String.valueOf(response.headers()
                                                                        .map()
                                                                        .get("Location")));
            log.info(String.valueOf(response.version()) + " POST request to {} returned {}", identifier, String
                    .valueOf(response.statusCode()));
        } catch (Exception ex) {
            throw new LdpClientException(ex.toString(), ex.getCause());
        }
    }

    @Override
    public void postWithMetadata(final IRI identifier, final InputStream stream, final Map<String, String> metadata)
            throws LdpClientException {
        try {
            requireNonNull(identifier, NON_NULL_IDENTIFIER);
            final URI uri = new URI(identifier.getIRIString());
            final String[] entries = buildHeaderEntryList(metadata);
            final HttpRequest req = HttpRequest.newBuilder(uri)
                                               .headers(entries)
                                               .POST(HttpRequest.BodyProcessor.fromInputStream(() -> stream))
                                               .build();
            final HttpResponse<String> response = client.send(req, asString());
            log.info("New Resource Location {}", String.valueOf(response.headers()
                                                                        .map()
                                                                        .get("Location")));
            log.info(String.valueOf(response.version()) + " POST request to {} returned {}", identifier, String
                    .valueOf(response.statusCode()));
        } catch (Exception ex) {
            throw new LdpClientException(ex.toString(), ex.getCause());
        }
    }

    @Override
    public void postWithAuth(final IRI identifier, final InputStream stream, final String contentType, final String
            authorization) throws LdpClientException {
        try {
            requireNonNull(identifier, NON_NULL_IDENTIFIER);
            final URI uri = new URI(identifier.getIRIString());
            final HttpRequest req = HttpRequest.newBuilder(uri)
                                               .headers(HttpHeaders.CONTENT_TYPE, contentType, HttpHeaders
                                                       .AUTHORIZATION, authorization)
                                               .POST(HttpRequest.BodyProcessor.fromInputStream(() -> stream))
                                               .build();
            final HttpResponse<String> response = client.send(req, asString());
            log.info("New Resource Location {}", String.valueOf(response.headers()
                                                                        .map()
                                                                        .get("Location")));
            log.info(String.valueOf(response.version()) + " AUTHORIZED POST request to {} returned {}", identifier,
                    String.valueOf(response.statusCode()));
        } catch (Exception ex) {
            throw new LdpClientException(ex.toString(), ex.getCause());
        }
    }

    @Override
    public void postSlug(final IRI identifier, final String slug, final InputStream stream, final String contentType)
            throws LdpClientException {
        try {
            requireNonNull(identifier, NON_NULL_IDENTIFIER);
            final URI uri = new URI(identifier.getIRIString());
            final HttpRequest req = HttpRequest.newBuilder(uri)
                                               .headers(HttpHeaders.CONTENT_TYPE, contentType, "Slug", slug)
                                               .POST(HttpRequest.BodyProcessor.fromInputStream(() -> stream))
                                               .build();
            final HttpResponse<String> response = client.send(req, asString());
            log.info("New Resource Location {}", String.valueOf(response.headers()
                                                                        .map()
                                                                        .get("Location")));
            log.info(String.valueOf(response.version()) + " POST request to {} returned {}", identifier, String
                    .valueOf(response.statusCode()));
        } catch (Exception ex) {
            throw new LdpClientException(ex.toString(), ex.getCause());
        }
    }

    @Override
    public void postBinaryWithDigest(final IRI identifier, final InputStream stream, final String contentType, final
    String digest) throws LdpClientException {
        try {
            requireNonNull(identifier, NON_NULL_IDENTIFIER);
            final URI uri = new URI(identifier.getIRIString());
            final HttpRequest req = HttpRequest.newBuilder(uri)
                                               .headers(HttpHeaders.CONTENT_TYPE, contentType, "Digest", digest)
                                               .POST(HttpRequest.BodyProcessor.fromInputStream(() -> stream))
                                               .build();
            final HttpResponse<String> response = client.send(req, asString());
            log.info("New Resource Location {}", String.valueOf(response.headers()
                                                                        .map()
                                                                        .get("Location")));
            log.info(String.valueOf(response.version()) + " POST request to {} returned {}", identifier, String
                    .valueOf(response.statusCode()));
        } catch (Exception ex) {
            throw new LdpClientException(ex.toString(), ex.getCause());
        }
    }

    @Override
    public void newLdpDc(final IRI identifier, final String slug, final IRI membershipObj) throws LdpClientException {
        try {
            requireNonNull(identifier, NON_NULL_IDENTIFIER);
            final URI uri = new URI(identifier.getIRIString());
            final String entity = "<> " + LDP.hasMemberRelation + " " + DC.isPartOf + " ;\n" + LDP.membershipResource
                    + " " + membershipObj;
            final HttpRequest req = HttpRequest.newBuilder(uri)
                                               .headers(HttpHeaders.CONTENT_TYPE, contentTypeTurtle, "Slug", slug,
                                                       HttpHeaders.LINK, LDP.DirectContainer + "; rel=\"type\"")
                                               .POST(HttpRequest.BodyProcessor.fromString(entity))
                                               .build();
            final HttpResponse<String> response = client.send(req, asString());
            log.info(String.valueOf(response.version()) + " POST create LDP-DC request to {} returned {}", uri,
                    String.valueOf(response.statusCode()));
        } catch (Exception ex) {
            throw new LdpClientException(ex.toString(), ex.getCause());
        }
    }

    @Override
    public void newLdpDcWithAuth(final IRI identifier, final String slug, final IRI membershipObj, final String
            authorization) throws LdpClientException {
        try {
            requireNonNull(identifier, NON_NULL_IDENTIFIER);
            final URI uri = new URI(identifier.getIRIString());
            final String[] headers = new String[]{HttpHeaders.CONTENT_TYPE, contentTypeTurtle, "Slug", slug,
                    HttpHeaders.LINK, LDP.DirectContainer + "; rel=\"type\"", HttpHeaders.AUTHORIZATION, authorization};
            final String entity = "<> " + LDP.hasMemberRelation + " " + DC.isPartOf + " ;\n" + LDP.membershipResource
                    + " " + membershipObj;
            final HttpRequest req = HttpRequest.newBuilder(uri)
                                               .headers(headers)
                                               .POST(HttpRequest.BodyProcessor.fromString(entity))
                                               .build();
            final HttpResponse<String> response = client.send(req, asString());
            log.info(String.valueOf(response.version()) + " POST create LDP-DC request to {} returned {}", uri,
                    String.valueOf(response.statusCode()));
        } catch (Exception ex) {
            throw new LdpClientException(ex.toString(), ex.getCause());
        }
    }

    @Override
    public void put(final IRI identifier, final InputStream stream, final String contentType) throws
            LdpClientException {
        try {
            requireNonNull(identifier, NON_NULL_IDENTIFIER);
            final URI uri = new URI(identifier.getIRIString());
            final HttpRequest req = HttpRequest.newBuilder(uri)
                                               .headers(HttpHeaders.CONTENT_TYPE, contentType)
                                               .PUT(HttpRequest.BodyProcessor.fromInputStream(() -> stream))
                                               .build();
            final HttpResponse<String> response = client.send(req, asString());
            log.info(String.valueOf(response.version()) + " PUT request to {} returned {}", identifier, String
                    .valueOf(response.statusCode()));
        } catch (Exception ex) {
            throw new LdpClientException(ex.toString(), ex.getCause());
        }
    }

    @Override
    public void putWithMetadata(final IRI identifier, final InputStream stream, final Map<String, String> metadata)
            throws LdpClientException {
        try {
            requireNonNull(identifier, NON_NULL_IDENTIFIER);
            final URI uri = new URI(identifier.getIRIString());
            final String[] entries = buildHeaderEntryList(metadata);
            final HttpRequest req = HttpRequest.newBuilder(uri)
                                               .headers(entries)
                                               .PUT(HttpRequest.BodyProcessor.fromInputStream(() -> stream))
                                               .build();
            final HttpResponse<String> response = client.send(req, asString());
            log.info(String.valueOf(response.version()) + " PUT request to {} returned {}", identifier, String
                    .valueOf(response.statusCode()));
        } catch (Exception ex) {
            throw new LdpClientException(ex.toString(), ex.getCause());
        }
    }

    @Override
    public void putWithAuth(final IRI identifier, final InputStream stream, final String contentType, final String
            authorization) throws LdpClientException {
        try {
            requireNonNull(identifier, NON_NULL_IDENTIFIER);
            final URI uri = new URI(identifier.getIRIString());
            final HttpRequest req = HttpRequest.newBuilder(uri)
                                               .headers(HttpHeaders.CONTENT_TYPE, contentType, HttpHeaders
                                                       .AUTHORIZATION, authorization)
                                               .PUT(HttpRequest.BodyProcessor.fromInputStream(() -> stream))
                                               .build();
            final HttpResponse<String> response = client.send(req, asString());
            log.info(String.valueOf(response.version()) + " AUTHORIZED PUT request to {} returned {}", identifier,
                    String.valueOf(response.statusCode()));
        } catch (Exception ex) {
            throw new LdpClientException(ex.toString(), ex.getCause());
        }
    }

    @Override
    public void putIfMatch(final IRI identifier, final InputStream stream, final String contentType, final String
            etag) throws LdpClientException {
        try {
            requireNonNull(identifier, NON_NULL_IDENTIFIER);
            final URI uri = new URI(identifier.getIRIString());
            final HttpRequest req = HttpRequest.newBuilder(uri)
                                               .headers(HttpHeaders.CONTENT_TYPE, contentType, HttpHeaders.ETAG, etag)
                                               .PUT(HttpRequest.BodyProcessor.fromInputStream(() -> stream))
                                               .build();
            final HttpResponse<String> response = client.send(req, asString());
            log.info(String.valueOf(response.version()) + " PUT request with matching Etag {} to {} returned {}",
                    etag, identifier, String.valueOf(response.statusCode()));
        } catch (Exception ex) {
            throw new LdpClientException(ex.toString(), ex.getCause());
        }
    }

    @Override
    public void putBinaryWithDigest(final IRI identifier, final InputStream stream, final String contentType, final
    String digest) throws LdpClientException {
        try {
            requireNonNull(identifier, NON_NULL_IDENTIFIER);
            final URI uri = new URI(identifier.getIRIString());
            final HttpRequest req = HttpRequest.newBuilder(uri)
                                               .headers(HttpHeaders.CONTENT_TYPE, contentType, "Digest", digest)
                                               .PUT(HttpRequest.BodyProcessor.fromInputStream(() -> stream))
                                               .build();
            final HttpResponse<String> response = client.send(req, asString());
            log.info(String.valueOf(response.version()) + " PUT request to {} returned {}", identifier, String
                    .valueOf(response.statusCode()));
        } catch (Exception ex) {
            throw new LdpClientException(ex.toString(), ex.getCause());
        }
    }

    @Override
    public void putIfUnmodified(final IRI identifier, final InputStream stream, final String contentType, final
    String time) throws LdpClientException {
        try {
            requireNonNull(identifier, NON_NULL_IDENTIFIER);
            final URI uri = new URI(identifier.getIRIString());
            final HttpRequest req = HttpRequest.newBuilder(uri)
                                               .headers(HttpHeaders.CONTENT_TYPE, contentType, "If-Unmodified-Since",
                                                       time)
                                               .PUT(HttpRequest.BodyProcessor.fromInputStream(() -> stream))
                                               .build();
            final HttpResponse<String> response = client.send(req, asString());
            log.info(String.valueOf(response.version()) + " PUT request to {} returned {}", identifier, String
                    .valueOf(response.statusCode()));
        } catch (Exception ex) {
            throw new LdpClientException(ex.toString(), ex.getCause());
        }
    }

    @Override
    public void delete(final IRI identifier) throws LdpClientException {
        try {
            requireNonNull(identifier, NON_NULL_IDENTIFIER);
            final URI uri = new URI(identifier.getIRIString());
            final HttpRequest req = HttpRequest.newBuilder(uri)
                                               .DELETE(HttpRequest.noBody())
                                               .build();
            final HttpResponse<String> response = client.send(req, asString());
            log.info(String.valueOf(response.version()) + " DELETE request to {} returned {}", identifier, String
                    .valueOf(response.statusCode()));
        } catch (Exception ex) {
            throw new LdpClientException(ex.toString(), ex.getCause());
        }
    }

    @Override
    public void patch(final IRI identifier, final InputStream stream) throws LdpClientException {
        try {
            requireNonNull(identifier, NON_NULL_IDENTIFIER);
            final URI uri = new URI(identifier.getIRIString());
            final HttpRequest req = HttpRequest.newBuilder(uri)
                                               .header(HttpHeaders.CONTENT_TYPE, WebContent.contentTypeSPARQLUpdate)
                                               .method("PATCH", HttpRequest.BodyProcessor.fromInputStream(() -> stream))
                                               .build();
            final HttpResponse<String> response = client.send(req, asString());
            log.info(String.valueOf(response.version()) + " PATCH request to {} returned {}", identifier, String
                    .valueOf(response.statusCode()));
        } catch (Exception ex) {
            throw new LdpClientException(ex.toString(), ex.getCause());
        }
    }

    @Override
    public void multipartOptions(final IRI identifier) {
        requireNonNull(identifier, NON_NULL_IDENTIFIER);
    }

    @Override
    public void multipartStart(final IRI identifier, final InputStream stream) {
        requireNonNull(identifier, NON_NULL_IDENTIFIER);
    }

    @Override
    public void multipartGet(final IRI identifier, final String sessionId) {
        requireNonNull(identifier, NON_NULL_IDENTIFIER);
    }

    @Override
    public void multipartPut(final IRI identifier, final InputStream stream, final String sessionId) {
        requireNonNull(identifier, NON_NULL_IDENTIFIER);
    }

    @Override
    public void multipartPost(final IRI identifier, final InputStream stream, final String sessionId) {
        requireNonNull(identifier, NON_NULL_IDENTIFIER);
    }

    @Override
    public void multipartDelete(final IRI identifier, final String sessionId) {
        requireNonNull(identifier, NON_NULL_IDENTIFIER);
    }

    @Override
    public void asyncPut(final IRI identifier, final InputStream stream) throws LdpClientException {
        try {
            requireNonNull(identifier, NON_NULL_IDENTIFIER);
            final URI uri = new URI(identifier.getIRIString());
            final HttpRequest req = HttpRequest.newBuilder(uri)
                                               .headers(HttpHeaders.CONTENT_TYPE, WebContent.contentTypeNTriples)
                                               .PUT(HttpRequest.BodyProcessor.fromInputStream(() -> stream))
                                               .build();
            final CompletableFuture<HttpResponse<String>> response = client.sendAsync(req, asString());
        } catch (Exception ex) {
            throw new LdpClientException(ex.toString(), ex.getCause());
        }
    }

    /**
     * @param query       a complete formatted URI string
     * @param contentType content Type as a {@link String}
     * @return body as byte[]
     * @throws LdpClientException an URISyntaxException, IOException or InterruptedException
     */
    public byte[] getBytesWithQuery(final String query, final String contentType) throws LdpClientException {
        try {
            final HttpRequest req = HttpRequest.newBuilder(new URI(query))
                                               .headers(HttpHeaders.CONTENT_TYPE, WebContent.contentTypeSPARQLQuery,
                                                       HttpHeaders.ACCEPT, contentType)
                                               .GET()
                                               .build();
            final HttpResponse<byte[]> response = client.send(req, asByteArray());

            log.info(String.valueOf(response.version()));
            log.info(String.valueOf(response.statusCode()));
            return response.body();
        } catch (Exception ex) {
            throw new LdpClientException(ex.toString(), ex.getCause());
        }
    }

    /**
     * @param query       a complete formatted URI string
     * @param contentType content Type as a {@link String}
     * @return body as {@link String}
     * @throws LdpClientException an URISyntaxException, IOException or InterruptedException
     */
    public String getQuery(final String query, final String contentType) throws LdpClientException {
        try {
            final HttpRequest req = HttpRequest.newBuilder(new URI(query))
                                               .headers(HttpHeaders.CONTENT_TYPE, WebContent.contentTypeSPARQLQuery,
                                                       HttpHeaders.ACCEPT, contentType)
                                               .GET()
                                               .build();
            final HttpResponse<String> response = client.send(req, asString());

            log.info(String.valueOf(response.version()));
            log.info(String.valueOf(response.statusCode()));
            return response.body();
        } catch (Exception ex) {
            throw new LdpClientException(ex.toString(), ex.getCause());
        }
    }

    /**
     * @param query       a complete formatted URI string
     * @param contentType content Type as a {@link String}
     * @return body as byte[]
     * @throws LdpClientException an URISyntaxException, IOException or InterruptedException
     */
    public byte[] asyncGetBytesWithQuery(final String query, final String contentType) throws LdpClientException {
        try {
            final HttpRequest req = HttpRequest.newBuilder()
                                               .uri(new URI(query))
                                               .headers(HttpHeaders.CONTENT_TYPE, WebContent.contentTypeSPARQLQuery,
                                                       HttpHeaders.ACCEPT, contentType)
                                               .GET()
                                               .build();
            final CompletableFuture<HttpResponse<byte[]>> response = client.sendAsync(req, asByteArray());
            log.info(String.valueOf(response.get()
                                            .version()));
            log.info(String.valueOf(response.get()
                                            .statusCode()));
            return response.get()
                           .body();
        } catch (Exception ex) {
            throw new LdpClientException(ex.toString(), ex.getCause());
        }
    }

    /**
     * @param query       a complete formatted URI string
     * @param contentType content Type as a {@link String}
     * @return body as {@link String}
     * @throws LdpClientException an URISyntaxException, IOException or InterruptedException
     */
    public String asyncGetQuery(final String query, final String contentType) throws LdpClientException {
        try {
            final HttpRequest req = HttpRequest.newBuilder()
                                               .uri(new URI(query))
                                               .headers(HttpHeaders.CONTENT_TYPE, WebContent.contentTypeSPARQLQuery,
                                                       HttpHeaders.ACCEPT, contentType)
                                               .GET()
                                               .build();
            final CompletableFuture<HttpResponse<String>> response = client.sendAsync(req, asString());
            log.info(String.valueOf(response.get()
                                            .version()));
            log.info(String.valueOf(response.get()
                                            .statusCode()));
            return response.get()
                           .body();
        } catch (Exception ex) {
            throw new LdpClientException(ex.toString(), ex.getCause());
        }
    }

    /**
     * @param identifier a sparql interface
     * @param query      a sparql query body
     * @return body as {@link String}
     * @throws LdpClientException an URISyntaxException, IOException or InterruptedException
     */
    public String syncUpdate(final IRI identifier, final String query) throws LdpClientException {
        try {
            requireNonNull(identifier, NON_NULL_IDENTIFIER);
            final URI uri = new URI(identifier.getIRIString());
            final String formdata = "update=" + query;
            final HttpRequest req = HttpRequest.newBuilder(uri)
                                               .headers(HttpHeaders.CONTENT_TYPE, WebContent.contentTypeSPARQLQuery)
                                               .POST(HttpRequest.BodyProcessor.fromString(formdata))
                                               .build();
            final HttpResponse<String> response = client.send(req, asString());
            log.info(String.valueOf(response.version()));
            log.info(String.valueOf(response.statusCode()));
            return response.body();
        } catch (Exception ex) {
            throw new LdpClientException(ex.toString(), ex.getCause());
        }
    }
}