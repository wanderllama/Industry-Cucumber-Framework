package jw.demo.stepDefinition;

import io.cucumber.java.en.And;
import io.cucumber.java.en.Given;

public class MyStepdefs {


    @Given("test runs")
    public void testRuns() {
//        System.out.println(TestContext.getScenarioCtx().getName());
    }

    //    @ParameterType("skip | pass | fail}")
    @And("test {string}")
    public void test(String result) {
        switch (result) {
            case "skip", "fail" -> throw new RuntimeException();
            case "pass" -> System.out.println("pass");
        }
    }

}
