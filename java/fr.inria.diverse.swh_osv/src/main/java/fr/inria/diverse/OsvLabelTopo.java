/*
 * Copyright (c) 2020 The Software Heritage developers
 * See the AUTHORS file at the top-level directory of this distribution
 * License: GNU General Public License version 3, or any later version
 * See top-level LICENSE file for more information
 */

package fr.inria.diverse;

import com.martiansoftware.jsap.*;

import fr.inria.diverse.Utils.TopoSort;
import fr.inria.diverse.Utils.TopoSort.MyLongBigArrayBigList;
import fr.inria.diverse.model.AffectedRevisions;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectOutputStream;
import java.nio.file.Paths;

public class OsvLabelTopo extends OsvLabelAbstract {
    private static final Logger logger = LogManager.getLogger(OsvLabelTopo.class);
    public static String partialResultFileName = "partial.bin";

    // Label
    AffectedRevisions affectedRevisions;

    public OsvLabelTopo(final String graphBasename, final String vulnRanges, final String outdir, boolean cache)
            throws IOException, ClassNotFoundException {
        super(graphBasename, vulnRanges, outdir, cache);
        affectedRevisions = new AffectedRevisions();

    }

    public void export() throws IOException {
        FileOutputStream fileOutputStream = new FileOutputStream(Paths.get(outdir, partialResultFileName).toString());
        ObjectOutputStream objectOutputStream = new ObjectOutputStream(fileOutputStream);
        objectOutputStream.writeObject(affectedRevisions);
        objectOutputStream.flush();
        objectOutputStream.close();

    }

    public MyLongBigArrayBigList getTopoOrder() {
        TopoSort topoSort = new TopoSort();
        MyLongBigArrayBigList result;
        try {
            result = topoSort.getTopoOrder(graph, "out/res.bin");
            return result;

        } catch (IOException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
        return null;
    }

    public static void main(final String[] args) throws IOException, InterruptedException, ClassNotFoundException {
        final JSAPResult config = parse_args(args);

        final String graphPath = config.getString("graphPath");
        final String outdir = config.getString("outdir");
        final String vulnPreprocessRangesPath = config.getString("vulnPreprocessRangesPath");
        final OsvLabelTopo tp = new OsvLabelTopo(graphPath, vulnPreprocessRangesPath, outdir, true);
        tp.getTopoOrder();
    }

    @Override
    public void labelRevisionGraph() throws InterruptedException {
        throw new UnsupportedOperationException("Unimplemented method 'labelRevisionGraph'");
    }

}
