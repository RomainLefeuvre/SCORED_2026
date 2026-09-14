package fr.inria.diverse.analysis;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.martiansoftware.jsap.FlaggedOption;
import com.martiansoftware.jsap.JSAP;
import com.martiansoftware.jsap.JSAPException;
import com.martiansoftware.jsap.JSAPResult;
import com.martiansoftware.jsap.Parameter;
import com.martiansoftware.jsap.SimpleJSAP;

import fr.inria.diverse.Utils.Progress;
import fr.inria.diverse.Utils.ToolBox;
import fr.inria.diverse.model.SwhVulnerabilityRange;
import fr.inria.diverse.model.VulnerableProvenanceMap;
import it.unimi.dsi.fastutil.io.BinIO;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import me.tongfei.progressbar.ProgressBar;

/**
 * The purpose of this class is to have a map between range and url and new
 * revision
 */
public class VulnerableRevisionPerUrlAnalysis {
    //Input --> Mapping between range to set of vulnerable revision
    Map<SwhVulnerabilityRange, LongOpenHashSet> rangeToNewVulnerableRevisions;
    //Provenance of vulnerable revision, --> retrieve origin from revision id
    VulnerableProvenanceMap provenance;

    private ConcurrentHashMap<Long, PrintWriter> threadToPrintWriter;
    private Gson gson;


    //all the list of origin(url) associated with vuln rev
    List<List<String>> urlsLists;
    Map<Integer, Long> urlListHashCodeToIndexInUrlsList;
    private String innerThreadFolder;

    public PrintWriter getThreadSafePrintWriter() {
        if (!threadToPrintWriter.containsKey(Thread.currentThread().getId())) {
            try {
                ToolBox.createParentIfNeeded(this.innerThreadFolder+"output" + Thread.currentThread().getId());
                File currentThreadFile = new File(this.innerThreadFolder + "output" + Thread.currentThread().getId());
                threadToPrintWriter.put(Thread.currentThread().getId(), new PrintWriter(currentThreadFile));
            } catch ( IOException exception) {
                exception.printStackTrace();
            }
        }
        return threadToPrintWriter.get(Thread.currentThread().getId());
    }

     
    // Entry<SwhVulnerabilityRange, Map<Long (id of fork url), Long (number of vulnerable revision associated to the url)>>  
                                         
    private void printFullEntry(Entry<SwhVulnerabilityRange, Map<Long, Long>> fullEntry) {
        JsonArray urlsToRevsArray = new JsonArray();
        fullEntry.getValue().entrySet().stream().forEach(entry -> urlsToRevsArray.add(entryToJsonObject(entry)));

        JsonObject currentItem = new JsonObject();
        currentItem.add("range", gson.toJsonTree(fullEntry.getKey()));

        JsonObject metadata = new JsonObject();
        metadata.add("url_to_new_vulnerable_revision", urlsToRevsArray);
        currentItem.add("metadata", metadata);
        this.getThreadSafePrintWriter().println(gson.toJson(currentItem));

    }

    private VulnerableRevisionPerUrlAnalysis computeRangeToUrlToNewVulnerableCommits() {
        try (ProgressBar pb = Progress.infoBar("Computing the ranges to urls to new vulnerable commits",
                logger,rangeToNewVulnerableRevisions.size())) {
            rangeToNewVulnerableRevisions.entrySet().parallelStream()
                    .forEach(entry -> {
                        pb.step();
                        Map<Long, Long> urlIndexTovulnerableCommitsNumber = new HashMap<>();
                        entry.getValue().forEach(revision -> {
                            List<String> urls = provenance.getUrls(revision);
                            Long indexOfUrls = urlListHashCodeToIndexInUrlsList.get(urls.hashCode());
                            urlIndexTovulnerableCommitsNumber.putIfAbsent(indexOfUrls, 1l);
                            urlIndexTovulnerableCommitsNumber.computeIfPresent(indexOfUrls, (key, value) -> ++value);
                        });
                        printFullEntry(Map.entry(entry.getKey(), urlIndexTovulnerableCommitsNumber));
                    });
        }
        logger.info("flushing and closing PrintWriters");
        this.threadToPrintWriter.values().forEach(pw -> {
            pw.flush();
            pw.close();
        });
        return this;
    }

    private VulnerableRevisionPerUrlAnalysis storeAsJson(String analysisOutputPath, String urlListListsOutputPath)
            throws IOException {
        ToolBox.createParentIfNeeded(urlListListsOutputPath);
        File urlListListsOutputFile = new File(urlListListsOutputPath);
        PrintWriter pwUrlList = new PrintWriter(urlListListsOutputFile);
        logger.info("creating urlListList.json");
        pwUrlList.print("[");
        Iterator<List<String>> urlsIterator = this.urlsLists.iterator();
        while (urlsIterator.hasNext()) {
            List<String> item = urlsIterator.next();
            pwUrlList.print(this.gson.toJsonTree(item));
            if (urlsIterator.hasNext()) {
                pwUrlList.println(",");
            }
        }
        pwUrlList.print("]");
        pwUrlList.close();

        ToolBox.createParentIfNeeded(analysisOutputPath);
        File jsonOutputFile = new File(analysisOutputPath);

        PrintWriter pw = new PrintWriter(jsonOutputFile);

        int rangeOriginalNumber = this.rangeToNewVulnerableRevisions.size();

        int newRangeNumber = 0;

        List<String> filesCreated = Stream.of(new File(this.innerThreadFolder).listFiles())
                .filter(file -> !file.isDirectory())
                .map(File::getAbsolutePath)
                .collect(Collectors.toList());
        try (ProgressBar pb = Progress.infoBar("Storing analysis output as json", logger, filesCreated.size()) ){
            Iterator<String> it = filesCreated.iterator();
            while (it.hasNext()) {
                String currentFilePath = it.next();
                String line;
                try (BufferedReader br = new BufferedReader(new FileReader(currentFilePath))) {
                    while ((line = br.readLine()) != null) {
                        newRangeNumber++;
                        pw.println(line);
                    }
                    pb.step();
                } catch (IOException e) {
                    e.printStackTrace();
                }

            }
            pw.close();
        }

        logger.info("ORIGINAL:" + rangeOriginalNumber + "\nNEW" + newRangeNumber);
        return this;
    }
    
   
    private JsonObject entryToJsonObject(Entry<Long, Long> currentEntry) {
        JsonObject currentUrlsToVulnRevs = new JsonObject();
        currentUrlsToVulnRevs.addProperty("urlsIndex", currentEntry.getKey());
        currentUrlsToVulnRevs.addProperty("vulnerable_revisions_number", currentEntry.getValue());
        return currentUrlsToVulnRevs;
    }
    
    public VulnerableRevisionPerUrlAnalysis(Map<SwhVulnerabilityRange, LongOpenHashSet> rangeToNewVulnerableCommits,
            VulnerableProvenanceMap provenanceMap, String innerThreadFolder) {
        this.rangeToNewVulnerableRevisions = rangeToNewVulnerableCommits;
        this.provenance = provenanceMap;
        this.gson = new Gson();
        this.urlsLists = new ArrayList<>();
        this.urlListHashCodeToIndexInUrlsList = new HashMap<>();
        this.threadToPrintWriter = new ConcurrentHashMap<>();
        this.innerThreadFolder = innerThreadFolder;
    }

    /**
     * 
     * the path to store url lists, if exists will not be recomputed
     * 
     * @param ListPaths
     * @return
     */
    private VulnerableRevisionPerUrlAnalysis computeUrlList(String ListPaths) {

        if ((new File(ListPaths + "urlsList.bin")).exists()
                && (new File(ListPaths + "urlListHashCodeToIndexInUrlsList.bin")).exists()) {
            try {
                logger.info("existing lists detected, initializing from them...");
                this.urlsLists = (List<List<String>>) BinIO.loadObject(ListPaths + "urlsList.bin");
                this.urlListHashCodeToIndexInUrlsList = (Map<Integer, Long>) BinIO
                        .loadObject(ListPaths + "urlListHashCodeToIndexInUrlsList.bin");
            } catch (ClassNotFoundException | IOException e) {
                e.printStackTrace();
            }
            return this;
        }
        //Will contain all the list of url associated with vulnerable revision
        ConcurrentHashMap<List<String>, Boolean> setUrlsList = new ConcurrentHashMap<>();// no concurrent set is
                                                                                         // available, using a CHashMap
                                                                                         // with booleans
        try (ProgressBar pb = Progress.infoBar("Computing UrlListList",logger,
                rangeToNewVulnerableRevisions.size())) {
            rangeToNewVulnerableRevisions.values().parallelStream().forEach(revisionsHashSet -> {
                pb.step();
                for (Long revision : revisionsHashSet) {
                    //Origin associated to each url
                    List<String> revisionUrls = provenance.getUrls(revision);
                    setUrlsList.put(revisionUrls, false);
                }
            });
        }
        logger.info("Puting urlsLists set in List");
        this.urlsLists = setUrlsList.keySet().parallelStream().collect(Collectors.toList());
        long index = 0;
        try (ProgressBar pb = Progress.infoBar("Computing map of urls to index",logger,
                this.urlsLists.size())) {
            for (List<String> urlStrings : this.urlsLists) {
                pb.step();
                this.urlListHashCodeToIndexInUrlsList.put(urlStrings.hashCode(), index++);
            }
        }
        logger.info("storing url lists..");
        try {
            ToolBox.createParentIfNeeded(ListPaths + "urlsList.bin");
            BinIO.storeObject(urlsLists, ListPaths + "urlsList.bin");
            ToolBox.createParentIfNeeded(ListPaths + "urlListHashCodeToIndexInUrlsList.bin");
            BinIO.storeObject(urlListHashCodeToIndexInUrlsList, ListPaths + "urlListHashCodeToIndexInUrlsList.bin");
        } catch (IOException e) {
            e.printStackTrace();
        }
        return this;
    }

    private static final Logger logger = LogManager.getLogger(VulnerableRevisionPerUrlAnalysis.class);

    @SuppressWarnings("unchecked")
    public static void main(String[] args) throws ClassNotFoundException, IOException {
        final JSAPResult config = parse_args(args);

        final String graphPath = config.getString("graphPath");
        final String vulnerableProvenancePath = config.getString("vulnerableProvenancePath");
        final String innerListStoringPath = config.getString("innerListStoringPath");
        final String threadTmpFolder = config.getString("threadTmpFolder");
        final String jsonOuputPath = config.getString("jsonOuputPath");
        final String rangeToVulnRev = config.getString("rangeToVulnRev");

        logger.info("loading range to new vulnerable commits...");
        Map<SwhVulnerabilityRange, LongOpenHashSet> rangeToNewVulnerableCommits = (Map<SwhVulnerabilityRange, LongOpenHashSet>) BinIO
                .loadObject(rangeToVulnRev);

        logger.info("loading provenanceMap...");
        VulnerableProvenanceMap provenanceMap = new VulnerableProvenanceMap(graphPath, vulnerableProvenancePath, true);

        logger.info("Starting analysis...");
        VulnerableRevisionPerUrlAnalysis analysis = new VulnerableRevisionPerUrlAnalysis(
                rangeToNewVulnerableCommits, provenanceMap, threadTmpFolder).computeUrlList(innerListStoringPath)
                .computeRangeToUrlToNewVulnerableCommits();
        analysis.storeAsJson(
                jsonOuputPath + "Metrics34.jsonl",
                jsonOuputPath + "urlist.json");
    }

    protected static JSAPResult parse_args(final String[] args) {
        JSAPResult config = null;
        try {
            final SimpleJSAP jsap = new SimpleJSAP(VulnerableRevisionPerUrlAnalysis.class.getName(),
                    "Computes a map of range to origin containing an introduced commit",
                    new Parameter[] {
                            new FlaggedOption("graphPath", JSAP.STRING_PARSER, JSAP.NO_DEFAULT, JSAP.REQUIRED, 'g',
                                    "graphPath", "Basename of the compressed graph"),
                            new FlaggedOption("vulnerableProvenancePath", JSAP.STRING_PARSER, JSAP.NO_DEFAULT,
                                    JSAP.REQUIRED, 'v',
                                    "vulnerableProvenancePath",
                                    "Path to the folder where the vulnerableProvenancePath will be stored, only used to retrieve the originindexes"),
                            new FlaggedOption("jsonOuputPath", JSAP.STRING_PARSER, JSAP.NO_DEFAULT,
                                    JSAP.REQUIRED, 'j',
                                    "jsonOuputPath",
                                    "Path to the folder that will contain the vulnerableRevisionAnalysis output(i.e. the urlList file and the jsonl output)"),
                            new FlaggedOption("innerListStoringPath", JSAP.STRING_PARSER,
                                    "outputs/VulnerableRevisionPerUrlAnalysis/",
                                    JSAP.NOT_REQUIRED, 'l',
                                    "innerListStoringPath",
                                    "Path to the folder where the binary lists will be stored, for backup in case of a crash"),
                            new FlaggedOption("threadTmpFolder", JSAP.STRING_PARSER,
                                    "outputs/VulnerableRevisionPerUrlAnalysis/threadTmpFiles/",
                                    JSAP.NOT_REQUIRED, 't',
                                    "threadTmpFolder",
                                    "Path to the folder where the tempory thread jsons will be stored"),
                            new FlaggedOption("rangeToVulnRev", JSAP.STRING_PARSER,
                                    JSAP.NO_DEFAULT,
                                    JSAP.REQUIRED, 'e',
                                    "rangeToVulnRev",
                                    "Path to output of the vulnerableRevisionAnalysis (i.e. a binary file with a Map from range to list of rev)")
                    });

            config = jsap.parse(args);
            if (jsap.messagePrinted()) {
                System.exit(1);
            }
        } catch (final JSAPException e) {
            e.printStackTrace();
        }
        return config;
    }
}
