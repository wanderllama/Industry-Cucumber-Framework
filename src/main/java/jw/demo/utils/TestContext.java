package jw.demo.utils;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.reflect.TypeToken;
import io.cucumber.java.Scenario;
import jw.demo.constants.Constants;
import jw.demo.managers.FileReaderManager;
import lombok.Getter;
import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.testng.asserts.SoftAssert;

import java.io.IOException;
import java.lang.reflect.Type;
import java.sql.Timestamp;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

public final class TestContext {

    private static final Logger LOG = LogManager.getLogger(TestContext.class);
    private static final Pattern SCENARIO_NAME_MATCHER = Pattern.compile(".*<-?\\d{4}>.*");
    @Getter
    private static boolean globalSetupComplete;
    @Getter
    private static int[] scenarioOutcome;
    @Getter
    private static String baseUrl;
    @Getter
    private static String envPass;
    @Getter
    private static String userPassword;
    private static JsonObject globalData;
    private static ThreadLocal<Scenario> scenario = new ThreadLocal<>();
    private static ThreadLocal<ScenarioContext> scenarioCtx = new ThreadLocal<>();

    private TestContext() {
        throw new IllegalStateException("Utility class");
    }

    public static void initGlobal() {
        //noinspection ResultOfMethodCallIgnored
//        FileReaderManager.getInstance().getValidationReader();
//        if (Boolean.TRUE.equals(FileReaderManager.getInstance().getConfigReader().getTrustStoreEnabled())) {
//            System.setProperty("javax.net.ssl.trustStore",
//                    FileReaderManager.getInstance().getConfigReader().getTrustStore());
//            System.setProperty("javax.net.ssl.trustStorePassword",
//                    FileReaderManager.getInstance().getConfigReader().getTrustStorePasswd());
//        }
        baseUrl = FileReaderManager.getInstance().getConfigReader().getBaseUrl();
        LOG.info("Using env.pass as: {}", baseUrl);
        envPass = FileReaderManager.getInstance().getConfigReader().getEnvPasswd();
        LOG.info("Using env.pass as: {}", envPass);
        userPassword = System.getenv(envPass);
        String datafile = System.getProperty("datafile");
        if (StringUtils.isBlank(datafile))
            datafile = FileReaderManager.getInstance().getConfigReader().getDataFile();
        LOG.info("Loading {} as input data file", datafile);
        try {
            globalData = DocumentUtil.getJsonObjectFromFile("data" + datafile);
        } catch (IOException e) {
            LOG.error(LogException.errorMessage(e));
        }
        LOG.debug("Test data scenario count [{}]", globalData.size());
        globalData.add(ApiUtil.TOKENS, new JsonObject());
        scenarioOutcome = new int[3];
        globalSetupComplete = true;
    }

    public static Scenario getScenario() {
        return scenario.get();
    }

    public static void initScenario(Scenario currentScenario) {
        scenario.set(currentScenario);
        scenarioCtx.set(new ScenarioContext());
        getScenarioCtx().setScenarioData(getInitScenarioData(getScenario().getName()));
        getScenarioCtx().setOrganizations(new ArrayList<>());
        getScenarioCtx().setSoftAssert(new SoftAssert());
        if (SCENARIO_NAME_MATCHER.matcher(currentScenario.getName()).matches()) {
            // TODO check if you need to handle parameterization in scenario name
            var variable = currentScenario.getName().substring(currentScenario.getName().indexOf('<') + 1,
                    currentScenario.getName().indexOf('>'));
            // TODO update to capture scenarios that require special setup based on defined scenario nomenclature
            //  or has other parameterized values to capture and assign to ScenarioCtx
            if (currentScenario.getName().contains("scenarioIdentifier")) {
                // do these steps
                var anotherVariable = currentScenario.getName().substring(currentScenario.getName().indexOf('<') + 1,
                        currentScenario.getName().indexOf('>'));
                getScenarioCtx().setOrgName(anotherVariable);
                LOG.debug("extra setup complete");
            } else {
                LOG.debug("no extra setup required");
            }
        }
        LOG.info("Starting Scenario [{}]", TestContext.getScenario().getName());
    }

    public static ScenarioContext getScenarioCtx() {
        return scenarioCtx.get();
    }

    public static JsonObject getData() {
        return globalData;
    }

    public static synchronized JsonObject setToken(String userName, String token) {
        var currentToken = new JsonObject();
        currentToken.addProperty(Constants.TOKEN, token);
        currentToken.addProperty(Constants.AUTHORIZATION, ApiUtil.BEARER + token);
        currentToken.addProperty(Constants.TIMESTAMP, new Timestamp(System.currentTimeMillis()).toString());
        getData().getAsJsonObject(ApiUtil.TOKENS).add(userName, currentToken);

        getScenarioCtx().setToken(token);
        getScenarioCtx().setAuthorization(ApiUtil.BEARER + getScenarioCtx().getToken());
        return currentToken;
    }

    public static JsonObject getInitScenarioData(String scenarioName) {
        if (!globalData.has(scenarioName)) {
            return new JsonObject();
        }
        return globalData.getAsJsonObject(scenarioName).deepCopy();
    }

    public static void tearDown() {
        scenario.remove();
        scenarioCtx.remove();
    }

    public static void setOrganizationId(String organizationId) {
        getScenarioCtx().setStrOrgId(organizationId);
        getScenarioCtx().setOrgId(Integer.parseInt(organizationId.replace("-", "")));
    }

    public static List<String> getListFromJsonArray(JsonArray jsonArr) {
        Type listType = new TypeToken<List<String>>() {
        }.getType();
        return new Gson().fromJson(jsonArr, listType);
    }

    public static void logToScenario(String log) {
        try {
            getScenario().log(String.format("%s - [%s] %s", Thread.currentThread().getName(),
                    OffsetDateTime.now(ZoneId.of(Constants.EASTERN_TIME_ZONE_ID))
                            .format(DateTimeFormatter.ofPattern(Constants.MODIFY_DATETIME)),
                    log));
        } catch (NullPointerException e) {
            LOG.error(LogException.errorMessage("Can not get scenario with TestContext.getScenario() in the logToscenario() lambda, so logging here\n" + log, e));
        }
    }

    public static JsonObject switchToken(String userName) {
        JsonObject tokenForUserName = getData().getAsJsonObject(ApiUtil.TOKENS).getAsJsonObject(userName).deepCopy();
        getScenarioCtx().setToken(tokenForUserName.get(Constants.TOKEN).getAsString());
        getScenarioCtx().setAuthorization(ApiUtil.BEARER + getScenarioCtx().getToken());
        return tokenForUserName;
    }

    public static boolean globalSetupComplete() {
        return globalSetupComplete;
    }
}
