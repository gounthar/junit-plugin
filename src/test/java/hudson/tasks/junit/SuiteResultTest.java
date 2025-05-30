/*
 * The MIT License
 *
 * Copyright (c) 2004-2009, Sun Microsystems, Inc., Kohsuke Kawaguchi, Erik Ramfelt, Xavier Le Vourch, Yahoo!, Inc.
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package hudson.tasks.junit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import hudson.XmlFile;
import java.io.File;
import java.io.FileWriter;
import java.io.PrintWriter;
import java.io.Writer;
import java.net.URISyntaxException;
import java.util.List;
import java.util.Map;
import org.apache.commons.io.FileUtils;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.jvnet.hudson.test.Issue;

/**
 * Test cases for parsing JUnit report XML files.
 * As there are no XML schema for JUnit xml files, Hudson needs to handle
 * varied xml files.
 *
 * @author Erik Ramfelt
 * @author Christoph Kutzinski
 */
class SuiteResultTest {

    @TempDir
    private File tmp;

    private File getDataFile(String name) throws URISyntaxException {
        return new File(SuiteResultTest.class.getResource(name).toURI());
    }

    private SuiteResult parseOne(File file) throws Exception {
        return parseOne(file, StdioRetention.DEFAULT);
    }

    private SuiteResult parseOne(File file, StdioRetention stdioRetention) throws Exception {
        List<SuiteResult> results = SuiteResult.parse(file, stdioRetention, false, false, null);
        assertEquals(1, results.size());
        return results.get(0);
    }

    private SuiteResult parseOneWithProperties(File file) throws Exception {
        List<SuiteResult> results = SuiteResult.parse(file, StdioRetention.DEFAULT, true, false, null);
        assertEquals(1, results.size());
        return results.get(0);
    }

    private List<SuiteResult> parseSuites(File file) throws Exception {
        return SuiteResult.parse(file, StdioRetention.DEFAULT, false, false, null);
    }

    @Issue("JENKINS-1233")
    @Test
    void testIssue1233() throws Exception {
        SuiteResult result = parseOne(getDataFile("junit-report-1233.xml"));

        List<CaseResult> cases = result.getCases();
        assertEquals("test.foo.bar.DefaultIntegrationTest", cases.get(0).getClassName(), "Class name is incorrect");
        assertEquals(
                "test.foo.bar.BundleResolverIntegrationTest", cases.get(1).getClassName(), "Class name is incorrect");
        assertEquals(
                "test.foo.bar.BundleResolverIntegrationTest", cases.get(2).getClassName(), "Class name is incorrect");
        assertEquals("test.foo.bar.ProjectSettingsTest", cases.get(3).getClassName(), "Class name is incorrect");
        assertEquals("test.foo.bar.ProjectSettingsTest", cases.get(4).getClassName(), "Class name is incorrect");
    }

    /**
     * JUnit report file is generated by SoapUI Pro 1.7.6
     */
    @Issue("JENKINS-1463")
    @Test
    void testIssue1463() throws Exception {
        SuiteResult result = parseOne(getDataFile("junit-report-1463.xml"));

        List<CaseResult> cases = result.getCases();
        for (CaseResult caseResult : cases) {
            assertEquals(
                    "WLI-FI-Tests-Fake",
                    caseResult.getClassName(),
                    "Test class name is incorrect in " + caseResult.getName());
        }
        assertEquals("IF_importTradeConfirmationToDwh", cases.get(0).getName(), "Test name is incorrect");
        assertEquals("IF_getAmartaDisbursements", cases.get(1).getName(), "Test name is incorrect");
        assertEquals("IF_importGLReconDataToDwh", cases.get(2).getName(), "Test name is incorrect");
        assertEquals("IF_importTradeInstructionsToDwh", cases.get(3).getName(), "Test name is incorrect");
        assertEquals("IF_getDeviationTradeInstructions", cases.get(4).getName(), "Test name is incorrect");
        assertEquals("IF_getDwhGLData", cases.get(5).getName(), "Test name is incorrect");
    }

    /**
     * JUnit report produced by TAP (Test Anything Protocol)
     */
    @Issue("JENKINS-1472")
    @Test
    void testIssue1472() throws Exception {
        List<SuiteResult> results = SuiteResult.parse(getDataFile("junit-report-1472.xml"), false, false, null);
        assertTrue(results.size() > 20); // lots of data here

        SuiteResult sr0 = results.get(0);
        SuiteResult sr1 = results.get(1);
        assertEquals("make_test.t_basic_lint_t", sr0.getName());
        assertEquals("make_test.t_basic_meta_t", sr1.getName());
        assertNotEquals(sr0.getStdout(), sr1.getStdout());
    }

    @Issue("JENKINS-2874")
    @Test
    void testIssue2874() throws Exception {
        SuiteResult result = parseOne(getDataFile("junit-report-2874.xml"));

        assertEquals("DummyTest", result.getName(), "test suite name");
    }

    @Test
    void testErrorDetails() throws Exception {
        SuiteResult result = parseOne(getDataFile("junit-report-errror-details.xml"));

        List<CaseResult> cases = result.getCases();
        for (CaseResult caseResult : cases) {
            assertEquals(
                    "some.package.somewhere.WhooHoo",
                    caseResult.getClassName(),
                    "Test class name is incorrect in " + caseResult.getName());
        }
        assertEquals(
                "this normally has the string like, expected mullet, but got bream",
                cases.get(0).getErrorDetails());
    }

    @Test
    void testSuiteResultPersistence() throws Exception {
        SuiteResult source = parseOne(getDataFile("junit-report-1233.xml"));

        File dest = File.createTempFile("testSuiteResultPersistence", ".xml");
        try {
            XmlFile xmlFile = new XmlFile(dest);
            xmlFile.write(source);

            SuiteResult result = (SuiteResult) xmlFile.read();
            assertNotNull(result);

            assertEquals(source.getName(), result.getName());
            assertEquals(source.getTimestamp(), result.getTimestamp());
            assertEquals(source.getDuration(), result.getDuration(), 0.1f);
            assertEquals(source.getStderr(), result.getStderr());
            assertEquals(source.getStdout(), result.getStdout());
            assertEquals(source.getCases().size(), result.getCases().size());
            assertNotNull(result.getCase("test.foo.bar.BundleResolverIntegrationTest.testGetBundle"));
        } finally {
            dest.delete();
        }
    }

    @Issue("JENKINS-6516")
    @Test
    void testSuiteStdioTrimming() throws Exception {
        File data = File.createTempFile("testSuiteStdioTrimming", ".xml");
        try {
            try (Writer w = new FileWriter(data)) {
                PrintWriter pw = new PrintWriter(w);
                pw.println("<testsuites name='x'>");
                pw.println("<testsuite failures='0' errors='0' tests='1' name='x'>");
                pw.println("<testcase name='x' classname='x'/>");
                pw.println("<system-out/>");
                pw.print("<system-err><![CDATA[");
                pw.println("First line is intact.");
                for (int i = 0; i < 100; i++) {
                    pw.println("Line #" + i + " might be elided.");
                }
                pw.println("Last line is intact.");
                pw.println("]]></system-err>");
                pw.println("</testsuite>");
                pw.println("</testsuites>");
                pw.flush();
            }
            SuiteResult sr = parseOne(data);
            assertEquals(1030, sr.getStderr().length(), sr.getStderr());
        } finally {
            data.delete();
        }
    }

    @Issue("JENKINS-27931")
    @Test
    void testSuiteStdioTrimmingRetainOnlyFailed() throws Exception {
        File data = File.createTempFile("testSuiteStdioTrimming", ".xml");
        try {
            try (Writer w = new FileWriter(data)) {
                PrintWriter pw = new PrintWriter(w);
                pw.println("<testsuites name='x'>");
                pw.println("<testsuite failures='0' errors='0' tests='1' name='x'>");
                pw.println("<testcase name='x' classname='x'/>");
                pw.println("<system-out/>");
                pw.print("<system-err><![CDATA[");
                pw.println("First line is intact.");
                for (int i = 0; i < 100; i++) {
                    pw.println("Line #" + i + " might be elided.");
                }
                pw.println("Last line is intact.");
                pw.println("]]></system-err>");
                pw.println("</testsuite>");
                pw.println("</testsuites>");
                pw.flush();
            }
            SuiteResult sr = parseOne(data, StdioRetention.FAILED);
            assertEquals(1030, sr.getStderr().length(), sr.getStderr());
        } finally {
            data.delete();
        }
    }

    @Test
    void testSuiteStdioTrimmingOnFail() throws Exception {
        File data = File.createTempFile("testSuiteStdioTrimming", ".xml");
        try {
            try (Writer w = new FileWriter(data)) {
                PrintWriter pw = new PrintWriter(w);
                pw.println("<testsuites name='x'>");
                pw.println("<testsuite failures='1' errors='0' tests='1' name='x'>");
                pw.println("<testcase name='x' classname='x'><error>oops</error></testcase>");
                pw.println("<system-out/>");
                pw.print("<system-err><![CDATA[");
                pw.println("First line is intact.");
                for (int i = 0; i < 10000; i++) {
                    pw.println("Line #" + i + " might be elided.");
                }
                pw.println("Last line is intact.");
                pw.println("]]></system-err>");
                pw.println("</testsuite>");
                pw.println("</testsuites>");
                pw.flush();
            }
            SuiteResult sr = parseOne(data);
            assertEquals(100032, sr.getStderr().length(), sr.getStderr());
        } finally {
            data.delete();
        }
    }

    @Issue("JENKINS-27931")
    @Test
    void testSuiteStdioTrimmingOnFailRetainOnlyFailed() throws Exception {
        File data = File.createTempFile("testSuiteStdioTrimming", ".xml");
        try {
            try (Writer w = new FileWriter(data)) {
                PrintWriter pw = new PrintWriter(w);
                pw.println("<testsuites name='x'>");
                pw.println("<testsuite failures='1' errors='0' tests='1' name='x'>");
                pw.println("<testcase name='x' classname='x'><error>oops</error></testcase>");
                pw.println("<system-out/>");
                pw.print("<system-err><![CDATA[");
                pw.println("First line is intact.");
                for (int i = 0; i < 10000; i++) {
                    pw.println("Line #" + i + " should be retained.");
                }
                pw.println("Last line is intact.");
                pw.println("]]></system-err>");
                pw.println("</testsuite>");
                pw.println("</testsuites>");
                pw.flush();
            }
            SuiteResult sr = parseOne(data, StdioRetention.FAILED);
            assertEquals(308933, sr.getStderr().length(), sr.getStderr());
        } finally {
            data.delete();
        }
    }

    @Issue("JENKINS-27931")
    @Test
    void testSuiteStdioTrimmingOnFailRetainAll() throws Exception {
        File data = File.createTempFile("testSuiteStdioTrimming", ".xml");
        try {
            try (Writer w = new FileWriter(data)) {
                PrintWriter pw = new PrintWriter(w);
                pw.println("<testsuites name='x'>");
                pw.println("<testsuite failures='1' errors='0' tests='1' name='x'>");
                pw.println("<testcase name='x' classname='x'><error>oops</error></testcase>");
                pw.println("<system-out/>");
                pw.print("<system-err><![CDATA[");
                pw.println("First line is intact.");
                for (int i = 0; i < 10000; i++) {
                    pw.println("Line #" + i + " should be retained.");
                }
                pw.println("Last line is intact.");
                pw.println("]]></system-err>");
                pw.println("</testsuite>");
                pw.println("</testsuites>");
                pw.flush();
            }
            SuiteResult sr = parseOne(data, StdioRetention.ALL);
            assertEquals(308933, sr.getStderr().length(), sr.getStderr());
        } finally {
            data.delete();
        }
    }

    @SuppressFBWarnings({"RV_RETURN_VALUE_IGNORED_BAD_PRACTICE", "DM_DEFAULT_ENCODING", "OS_OPEN_STREAM"})
    @Test
    void testSuiteStdioTrimmingSurefire() throws Exception {
        File data = File.createTempFile("TEST-", ".xml");
        try {
            Writer w = new FileWriter(data);
            try {
                PrintWriter pw = new PrintWriter(w);
                pw.println("<testsuites name='x'>");
                pw.println("<testsuite failures='0' errors='0' tests='1' name='x'>");
                pw.println("<testcase name='x' classname='x'/>");
                pw.println("</testsuite>");
                pw.println("</testsuites>");
                pw.flush();
            } finally {
                w.close();
            }
            File data2 =
                    new File(data.getParentFile(), data.getName().replaceFirst("^TEST-(.+)[.]xml$", "$1-output.txt"));
            try {
                w = new FileWriter(data2);
                try {
                    PrintWriter pw = new PrintWriter(w);
                    pw.println("First line is intact.");
                    for (int i = 0; i < 100; i++) {
                        pw.println("Line #" + i + " might be elided.");
                    }
                    pw.println("Last line is intact.");
                    pw.flush();
                } finally {
                    w.close();
                }
                SuiteResult sr = parseOne(data);
                assertEquals(1030, sr.getStdout().length(), sr.getStdout());
            } finally {
                data2.delete();
            }
        } finally {
            data.delete();
        }
    }

    @SuppressFBWarnings({"RV_RETURN_VALUE_IGNORED_BAD_PRACTICE", "DM_DEFAULT_ENCODING", "OS_OPEN_STREAM"})
    @Test
    void testSuiteStdioTrimmingSurefireOnFail() throws Exception {
        File data = File.createTempFile("TEST-", ".xml");
        try {
            Writer w = new FileWriter(data);
            try {
                PrintWriter pw = new PrintWriter(w);
                pw.println("<testsuites name='x'>");
                pw.println("<testsuite failures='1' errors='0' tests='1' name='x'>");
                pw.println("<testcase name='x' classname='x'><error>oops</error></testcase>");
                pw.println("</testsuite>");
                pw.println("</testsuites>");
                pw.flush();
            } finally {
                w.close();
            }
            File data2 =
                    new File(data.getParentFile(), data.getName().replaceFirst("^TEST-(.+)[.]xml$", "$1-output.txt"));
            try {
                w = new FileWriter(data2);
                try {
                    PrintWriter pw = new PrintWriter(w);
                    pw.println("First line is intact.");
                    for (int i = 0; i < 10000; i++) {
                        pw.println("Line #" + i + " might be elided.");
                    }
                    pw.println("Last line is intact.");
                    pw.flush();
                } finally {
                    w.close();
                }
                SuiteResult sr = parseOne(data);
                assertEquals(100032, sr.getStdout().length(), sr.getStdout());
            } finally {
                data2.delete();
            }
        } finally {
            data.delete();
        }
    }

    @SuppressFBWarnings({"RV_RETURN_VALUE_IGNORED_BAD_PRACTICE", "DM_DEFAULT_ENCODING", "OS_OPEN_STREAM"})
    @Issue("JENKINS-27931")
    @Test
    void testSuiteStdioTrimmingSurefireOnFailRetainAll() throws Exception {
        File data = File.createTempFile("TEST-", ".xml");
        try {
            Writer w = new FileWriter(data);
            try {
                PrintWriter pw = new PrintWriter(w);
                pw.println("<testsuites name='x'>");
                pw.println("<testsuite failures='1' errors='0' tests='1' name='x'>");
                pw.println("<testcase name='x' classname='x'><error>oops</error></testcase>");
                pw.println("</testsuite>");
                pw.println("</testsuites>");
                pw.flush();
            } finally {
                w.close();
            }
            File data2 =
                    new File(data.getParentFile(), data.getName().replaceFirst("^TEST-(.+)[.]xml$", "$1-output.txt"));
            try {
                w = new FileWriter(data2);
                try {
                    PrintWriter pw = new PrintWriter(w);
                    pw.println("First line is intact.");
                    for (int i = 0; i < 10000; i++) {
                        pw.println("Line #" + i + " should be retained.");
                    }
                    pw.println("Last line is intact.");
                    pw.flush();
                } finally {
                    w.close();
                }
                SuiteResult sr = parseOne(data, StdioRetention.ALL);
                assertEquals(data2.length(), sr.getStdout().length(), sr.getStdout());
            } finally {
                data2.delete();
            }
        } finally {
            data.delete();
        }
    }

    @Issue("JENKINS-71139")
    @Test
    void stdioNull() throws Exception {
        SuiteResult sr = parseOne(getDataFile("TEST-null.xml"));
        XmlFile f = new XmlFile(TestResultAction.XSTREAM, File.createTempFile("junitResult.xml", null, tmp));
        f.write(sr);
        FileUtils.copyFile(f.getFile(), System.out);
        System.out.println();
        sr = (SuiteResult) f.read();
        System.out.println(TestResultAction.XSTREAM.toXML(sr));
        assertEquals("foo^@bar", sr.getStdout());
    }

    /**
     * When the testcase fails to initialize (exception in constructor or @Before)
     * there is no 'testcase' element at all.
     */
    @Issue("JENKINS-6700")
    @Test
    void testErrorInTestInitialization() throws Exception {
        SuiteResult suiteResult = parseOne(getDataFile("junit-report-6700.xml"));

        assertEquals(1, suiteResult.getCases().size());

        CaseResult result = suiteResult.getCases().get(0);
        assertEquals(1, result.getFailCount());
        assertNotNull(result.getErrorStackTrace());
    }

    @Issue("JENKINS-6545")
    @Test
    void testParseNestedTestSuites() throws Exception {
        // A report with several nested suites
        // 3 of them have actual some tests - each exactly one
        List<SuiteResult> results = parseSuites(getDataFile("junit-report-nested-testsuites.xml"));
        assertEquals(3, results.size());

        for (SuiteResult result : results) {
            assertEquals(1, result.getCases().size());
        }
    }

    @Test
    void testTestSuiteTimeAttribute() throws Exception {
        // A report with blocks of testsuites some with and some without time attrs
        List<SuiteResult> results = parseSuites(getDataFile("junit-report-testsuite-time-attrs.xml"));
        assertEquals(2503.1f, results.get(0).getDuration(), 2); // testsuit time
        assertEquals(22.0f, results.get(1).getDuration(), 2); // sum of test cases time
        assertEquals(40.0f, results.get(2).getDuration(), 2); // testsuit time
        assertEquals(20.0f, results.get(3).getDuration(), 2); // sum of test cases time
    }

    @Test
    void testTestParseTimeMethod() {
        // Tests parseTime() with various valid and invalid datetimes
        SuiteResult emptyResult = new SuiteResult("Test parseTime", "", "", null);
        assertEquals(0, emptyResult.parseTime("1970-01-01T00:00:00.00"));
        assertEquals(1704280980000L, emptyResult.parseTime("2024-01-03T11:23:00.00"));
        assertEquals(1704284831000L, emptyResult.parseTime("2024-01-03T12:27:11"));
        assertEquals(1704285613000L, emptyResult.parseTime("2024-01-03T 12:40:13"));
        assertEquals(1704284864000L, emptyResult.parseTime("2024-01-03T12:27:44Z"));
        assertEquals(1704281235000L, emptyResult.parseTime("2024-01-03T12:27:15+01:00"));
        assertEquals(1704288431210L, emptyResult.parseTime("2024-01-03T12:27:11.21-01:00"));
        assertEquals(-1, emptyResult.parseTime("2024-01-03T12:27:11.21+1:00"));
        assertEquals(-1, emptyResult.parseTime("2024-01-03 12:27:54Z"));
        assertEquals(-1, emptyResult.parseTime("2024-01-03"));
        assertEquals(-1, emptyResult.parseTime(""));
    }

    @Test
    void testProperties() throws Exception {
        SuiteResult sr = parseOneWithProperties(getDataFile("junit-report-with-properties.xml"));
        Map<String, String> props = sr.getProperties();
        assertEquals("value1", props.get("prop1"));
        String[] lines = props.get("multiline").split("\n");
        assertEquals("", lines[0]);
        assertEquals("          Config line 1", lines[1]);
        assertEquals("          Config line 2", lines[2]);
        assertEquals("          Config line 3", lines[3]);

        assertEquals(2, sr.getCases().size());
        CaseResult cr;
        cr = sr.getCase("io.jenkins.example.with.properties.testCaseA");
        assertEquals("description of test testCaseA", cr.getProperties().get("description"));
        cr = sr.getCase("io.jenkins.example.with.properties.testCaseZ");
        assertEquals(0, cr.getProperties().size());
    }
}
