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

import fr.inria.diverse.Utils.ToolBox;
import fr.inria.diverse.model.EventType;
import fr.inria.diverse.model.HashedExport;
import fr.inria.diverse.model.SwhVulnerabilityRange;
import fr.inria.diverse.model.VulnerabilityRange;
import it.unimi.dsi.big.webgraph.LazyLongIterator;
import it.unimi.dsi.fastutil.io.BinIO;
import me.tongfei.progressbar.DelegatingProgressBarConsumer;
import me.tongfei.progressbar.ProgressBar;
import me.tongfei.progressbar.ProgressBarBuilder;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.softwareheritage.graph.*;

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.nio.file.Paths;
import java.util.*;

public abstract class OsvLabelAbstract {
    private static final Logger logger = LogManager.getLogger(OsvLabelAbstract.class);
    public static String partialResultFileName = "ranges.bin";
    // private final SwhBidirectionalGraph graph;
    protected final SwhBidirectionalGraph graph;
    static ThreadLocal<SwhBidirectionalGraph> threadGraph = new ThreadLocal<>();
    protected final String outdir;
    boolean cache;

    // Input, augmented and boun
    protected List<SwhVulnerabilityRange> swhVulnerabilityRanges;

    public SwhBidirectionalGraph getThreadSafeeGraph() {
        if (threadGraph.get() == null) {
            threadGraph.set(graph.copy());
            logger.info("Copying Graph");
        }
        return threadGraph.get();
    }

    public OsvLabelAbstract(final String graphBasename, String vulnRangesPath, final String outdir,
            boolean cache)
            throws IOException, ClassNotFoundException {
        this.cache = cache;
        logger.info("Loading graph " + graphBasename + " ...");
        this.graph = SwhBidirectionalGraph.loadMapped(graphBasename);
        // this.graph.loadCommitterTimestamps();
        this.outdir = outdir;
        logger.info("Graph loaded.");
        this.swhVulnerabilityRanges = (ArrayList<SwhVulnerabilityRange>) BinIO.loadObject(vulnRangesPath);

    }

    abstract public void export() throws IOException;

    protected static JSAPResult parse_args(final String[] args) {
        JSAPResult config = null;
        try {
            final SimpleJSAP jsap = new SimpleJSAP(OsvLabel.class.getName(), "",
                    new Parameter[] {
                            new FlaggedOption("graphPath", JSAP.STRING_PARSER, JSAP.NO_DEFAULT, JSAP.REQUIRED, 'g',
                                    "graphPath", "Basename of the compressed graph"),
                            new FlaggedOption("vulnPreprocessRangesPath", JSAP.STRING_PARSER, JSAP.NO_DEFAULT,
                                    JSAP.REQUIRED, 'v',
                                    "vulnPreprocessRangesPath", "Osv preprocessed Range list"),
                            new FlaggedOption("outdir", JSAP.STRING_PARSER, JSAP.NO_DEFAULT, JSAP.REQUIRED, 'o',
                                    "outdir", "Directory where to put the results") });

            config = jsap.parse(args);
            if (jsap.messagePrinted()) {
                System.exit(1);
            }
        } catch (final JSAPException e) {
            e.printStackTrace();
        }
        return config;
    }

    abstract public void labelRevisionGraph() throws InterruptedException;

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

    public static <T extends HashedExport> T deserializeHashedExport(String path, int inputHash) {
        T hashedExport = null;

        try (FileInputStream file = new FileInputStream(path);
                ObjectInputStream in = new ObjectInputStream(file)) {

            // Method for deserialization of object
            try {
                hashedExport = (T) in.readObject();
            } catch (ClassNotFoundException | IOException e) {
                logger.warn(e + " " + path);

                return null;
            }

            if (hashedExport.getInputHash() == (inputHash)) {
                return hashedExport;
            } else {
                logger.warn("hash non equal for " + path);
                return null;
            }

        } catch (IOException e) {
            logger.debug(e);
            return null;
        }

    }

    public static <T extends HashedExport> void serializeHashedExport(String path, T obj) throws IOException {

        // Saving of object in a file
        FileOutputStream file = new FileOutputStream(path);
        ObjectOutputStream out = new ObjectOutputStream(file);

        // Method for serialization of object
        out.writeObject(obj);

        out.close();
        file.close();

    }
}
