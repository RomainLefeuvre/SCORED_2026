/*
 * Copyright (c) 2020 The Software Heritage developers
 * See the AUTHORS file at the top-level directory of this distribution
 * License: GNU General Public License version 3, or any later version
 * See top-level LICENSE file for more information
 */

package fr.inria.diverse;

import com.google.gson.Gson;
import com.google.gson.JsonIOException;
import com.google.gson.JsonSyntaxException;
import com.google.gson.reflect.TypeToken;
import com.martiansoftware.jsap.*;

import fr.inria.diverse.model.SwhVulnerabilityRange;
import fr.inria.diverse.model.VulnerabilityRange;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.softwareheritage.graph.*;

import java.io.FileReader;
import java.io.IOException;
import java.util.*;

public abstract class GraphProcess {
    private static final Logger logger = LogManager.getLogger(GraphProcess.class);
    public static String partialResultFileName = "ranges.bin";
    // private final SwhBidirectionalGraph graph;
    protected final SwhBidirectionalGraph graph;
    static ThreadLocal<SwhBidirectionalGraph> threadGraph = new ThreadLocal<>();
    protected final String outfile;
    boolean cache;
    List<VulnerabilityRange> vulnRanges;

    // Input, augmented and boun
    protected ArrayList<SwhVulnerabilityRange> swhVulnerabilityRanges;

    public SwhBidirectionalGraph getThreadSafeeGraph() {
        if (threadGraph.get() == null) {
            threadGraph.set(graph.copy());
            logger.info("Copying Graph");
        }
        return threadGraph.get();
    }

    public GraphProcess(final String graphBasename, final List<VulnerabilityRange> vulnRanges, final String outFile,
            boolean cache)
            throws IOException {
        this.vulnRanges = vulnRanges;
        this.cache = cache;
        logger.info("Loading graph " + graphBasename + " ...");
        this.graph = SwhBidirectionalGraph.loadMapped(graphBasename);
        // this.graph.loadCommitterTimestamps();
        this.outfile = outFile;
        logger.info("Graph loaded.");
    }

    public static List<VulnerabilityRange> deserializeVulnRanges(final String jsonFilePath) {
        try (FileReader reader = new FileReader(jsonFilePath)) {
            final Gson gson = new Gson();
            final List<VulnerabilityRange> data = gson.fromJson(reader, new TypeToken<ArrayList<VulnerabilityRange>>() {
            }.getType());
            return data;
        } catch (JsonSyntaxException | JsonIOException | IOException e) {
            throw new RuntimeException("Error while reading ranges", e);
        }
    }

    abstract public GraphProcess execute();

    abstract public GraphProcess export() throws IOException;

    protected static JSAPResult parse_args(final String[] args) {
        JSAPResult config = null;
        try {
            final SimpleJSAP jsap = new SimpleJSAP(OsvLabel.class.getName(), "",
                    new Parameter[] {
                            new FlaggedOption("graphPath", JSAP.STRING_PARSER, JSAP.NO_DEFAULT, JSAP.REQUIRED, 'g',
                                    "graphPath", "Basename of the compressed graph"),
                            new FlaggedOption("vulnRangesPath", JSAP.STRING_PARSER, JSAP.NO_DEFAULT, JSAP.REQUIRED, 'v',
                                    "vulnRangesPath", "Osv Range list"),
                            new FlaggedOption("outdir", JSAP.STRING_PARSER, JSAP.NO_DEFAULT, JSAP.REQUIRED, 'o',
                                    "outdir", "Directory where to put the results"),
                            new FlaggedOption("vulnRangeTemp", JSAP.STRING_PARSER, JSAP.NO_DEFAULT, JSAP.REQUIRED, 't',
                                    "vulnRangeTemp", "Temp directory where to put the range with nodeids"),
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

    /**
     * to delete
     * 
     * @param url
     * @return
     * @throws IOException
     */
    @Deprecated
    abstract GraphProcess exportRangesToFile(String url) throws IOException;
}
