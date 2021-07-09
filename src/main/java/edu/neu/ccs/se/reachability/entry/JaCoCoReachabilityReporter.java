package edu.neu.ccs.se.reachability.entry;

import org.apache.commons.io.FileUtils;
import org.jacoco.core.analysis.*;
import org.jacoco.core.internal.analysis.BundleCoverageImpl;
import org.jacoco.core.tools.ExecFileLoader;
import org.jacoco.report.DirectorySourceFileLocator;
import org.jacoco.report.FileMultiReportOutput;
import org.jacoco.report.IReportVisitor;
import org.jacoco.report.html.HTMLFormatter;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Scanner;
import java.util.stream.Collectors;

public class JaCoCoReachabilityReporter {
    public static void main(String[] args) throws IOException {
        if (args.length != 4) {
            System.err.println("Usage: java edu.neu.ccs.se.reachability.entry.JaCoCoReachabilityReporter classpath jacocoExecFile reachabilityFile reportDir");
            return;
        }
        String reachabilityInfo = args[2];
        Scanner rdr = new Scanner(new File(reachabilityInfo));
        while (rdr.hasNextLine()) {
            String line = rdr.nextLine();
            String[] data = line.split("\t");
            String className = data[0].replace('.', '/');
            int lineNumber = Integer.parseInt(data[2]);
            if (!coverableLines.containsKey(className)) {
                coverableLines.put(className, new HashSet<>());
            }
            coverableLines.get(className).add(lineNumber);
        }
        rdr.close();
        reportCoverage(args[1], args[0], args[3]);
    }

    static HashMap<String, HashSet<Integer>> coverableLines = new HashMap<>();
    static int linesCovered = 0;
    static int linesCoverable = 0;
    static int linesTotal = 0;

    public static void reportCoverage(String jacocoExecFile, String cp, String reportDir) throws IOException {



        ExecFileLoader loader = new ExecFileLoader();
        final CoverageBuilder builder = new CoverageBuilder();
        loader.load(new BufferedInputStream(new FileInputStream(jacocoExecFile)));
        Analyzer analyzer = new Analyzer(loader.getExecutionDataStore(), new ICoverageVisitor() {

            @Override
            public void visitCoverage(IClassCoverage iClassCoverage) {
                if (coverableLines.containsKey(iClassCoverage.getName()) &&
                iClassCoverage.getName().startsWith("org/apache/maven/model")) {
                    for (IMethodCoverage iMethodCoverage : iClassCoverage.getMethods()) {
                        boolean addedAllLinesAsCoverable = false;
                        if (iMethodCoverage.containsCode()) {
                            if(iMethodCoverage.getName().equals("parseBuild")) {
                                ICounter branchCounter = iMethodCoverage.getBranchCounter();
                                System.out.println(iMethodCoverage.getName()+iMethodCoverage.getDesc()+":"+branchCounter.getCoveredCount());
                            }

                                for (int i = iMethodCoverage.getFirstLine(); i <= iMethodCoverage.getLastLine(); i++) {
                                int lineStatus = iMethodCoverage.getLine(i).getStatus();
                                //if(iMethodCoverage.getName().equals("parseBuild")){
                                //    int branchStatus = iMethodCoverage.getLine(i).getBranchCounter().getStatus();
                                //    System.out.println("parseBuild#"+i+":"+lineStatus+"\t"+branchStatus);
                                //}
                                if (lineStatus == ICounter.EMPTY)
                                    continue;
                                linesTotal++;
                                boolean isReachableBySoot = coverableLines.get(iClassCoverage.getName()).contains(i);
                                if (lineStatus == ICounter.FULLY_COVERED || lineStatus == ICounter.PARTLY_COVERED) {
                                    linesCovered++;
                                    if (isReachableBySoot) {
                                        linesCoverable++;
                                    } else if (!addedAllLinesAsCoverable) {
                                        addedAllLinesAsCoverable = true;
                                        for(int j = iMethodCoverage.getFirstLine(); j<= iMethodCoverage.getLastLine(); j++){
                                            int status = iMethodCoverage.getLine(j).getStatus();
                                            if(status != ICounter.EMPTY && !coverableLines.get(iClassCoverage.getName()).contains(j)){
                                                System.out.println("Covered by test but not soot: " + iClassCoverage.getName() + ":" + j);
                                                linesCoverable++;
                                            }
                                        }
                                    }
                                } else if (isReachableBySoot) {
                                    linesCoverable++;
                                }
                            }
                        }
                    }
                }
            }
        });
        for (String path : cp.split(":")) {
            try {
                analyzer.analyzeAll(new File(path));
            } catch (IOException ex) {
                ex.printStackTrace();
            }
        }
        System.out.format("Final Reachable coverage: %d/%d (%.2f%%)\n", linesCovered, linesCoverable, (100d * linesCovered) / linesCoverable);
        System.out.format("Coverage considering all statements: %d/%d (%.2f%%)\n", linesCovered, linesTotal, (100d * linesCovered) / linesTotal);

        CoverageBuilder coverageBuilder = new CoverageBuilder();
        analyzer = new Analyzer(loader.getExecutionDataStore(), coverageBuilder);
        for (String path : cp.split(":")) {
            try {
                analyzer.analyzeAll(new File(path));
            } catch (IOException ex) {
                ex.printStackTrace();
            }
        }
        createHTMLReport(loader, coverageBuilder.getBundle("JQF"), reportDir);

    }

    public static void createHTMLReport(ExecFileLoader execFileLoader, IBundleCoverage bundleCoverage, String reportDir) throws IOException {

        IBundleCoverage filtered = new BundleCoverageImpl(bundleCoverage.getName(), bundleCoverage.getPackages().stream().filter(iPackageCoverage -> iPackageCoverage.getName().startsWith("org/apache/maven/model")).collect(Collectors.toList()));
        //taken from https://www.eclemma.org/jacoco/trunk/doc/examples/java/ReportGenerator.java under EPL
        final HTMLFormatter htmlFormatter = new HTMLFormatter();
        final IReportVisitor visitor = htmlFormatter
                .createVisitor(new FileMultiReportOutput(new File(reportDir)));

        // Initialize the report with all of the execution and session
        // information. At this point the report doesn't know about the
        // structure of the report being created
        visitor.visitInfo(execFileLoader.getSessionInfoStore().getInfos(),
                execFileLoader.getExecutionDataStore().getContents());

        // Populate the report structure with the bundle coverage information.
        // Call visitGroup if you need groups in your report.
        visitor.visitBundle(filtered,
                new DirectorySourceFileLocator(new File("/Users/jon/Documents/GMU/Projects/jqf/examples/target/sources"), "utf-8", 4));

        // Signal end of structure information to allow report to write all
        // information out
        visitor.visitEnd();
        addReachabilityToReport(reportDir);
    }
    public static void addReachabilityToReport(String reportDir) throws IOException {
        //Rewrite report files to include reachability info
        Files.walkFileTree(Paths.get(reportDir), new FileVisitor<Path>() {
            @Override
            public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                if (file.getFileName().toString().endsWith(".java.html")) {
                    Document doc = Jsoup.parse(file.toFile(), "utf8");

                    String className = file.getParent().getFileName().toString().replace('.', '/') + "/" + file.getFileName().toString().replace(".java.html", "");
                    if (coverableLines.containsKey(className)) {
                        HashSet<Integer> linesCoverable = coverableLines.get(className);
                        for (Element e : doc.select(".nc")) {
                            Integer lineNumber = Integer.parseInt(e.id().substring(1));
                            if (linesCoverable.contains(lineNumber)) {
                                e.addClass("coverable");
                            } else {
                                e.addClass("notCoverable");
                            }
                        }
                        FileUtils.write(file.toFile(), doc.outerHtml(), StandardCharsets.UTF_8);
                        return FileVisitResult.CONTINUE;
                    }
                }
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFileFailed(Path file, IOException exc) throws IOException {
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
                return FileVisitResult.CONTINUE;
            }
        });
        //Hack on the CSS too
        File cssFile = Paths.get(reportDir,"jacoco-resources","report.css").toFile();
        String css = FileUtils.readFileToString(cssFile, "utf8");
        css = css + "\npre.source span.notCoverable {\n" +
                "    opacity: 0.5;\n" +
                "}";
        FileUtils.writeStringToFile(cssFile, css, "utf8");
    }
}
