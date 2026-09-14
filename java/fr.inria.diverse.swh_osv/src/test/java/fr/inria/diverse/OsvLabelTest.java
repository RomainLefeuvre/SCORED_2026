/*
 * Copyright (c) 2020 The Software Heritage developers
 * See the AUTHORS file at the top-level directory of this distribution
 * License: GNU General Public License version 3, or any later version
 * See top-level LICENSE file for more information
 */

package fr.inria.diverse;

import static org.junit.Assert.assertEquals;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import org.junit.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import fr.inria.diverse.model.AffectedRevisions;
import fr.inria.diverse.model.VulnerabilityRange;
import it.unimi.dsi.fastutil.io.BinIO;

public class OsvLabelTest {
       
        @ParameterizedTest
        @MethodSource("argumentProvider")
        public void testSimplelabel(String datasetPath, String rangeFile, Map<String, List<String>> expected)
                        throws IOException, InterruptedException, ClassNotFoundException {
                final String graphPath = this.getClass().getResource("/dataset/").getPath().concat(datasetPath)
                                .concat("compressed/example");
                final String outdir = this.getClass().getResource("/output").getPath();
                final String vulnRangesPath = this.getClass().getResource("/ranges/").getPath().concat(rangeFile);
                List<VulnerabilityRange> vulns = OsvLabelAbstract.deserializeVulnRanges(vulnRangesPath);
                BinIO.storeObject(vulns, this.getClass().getResource("/output").getPath().concat("preprocess.bin"));
                
                PreProcess pp = new PreProcess(graphPath, vulns, outdir+"/preprocessRanges.bin", false,outdir+".cache/swhVulnRange.temp");
                pp.prepareRanges().export();

                final OsvLabel tp = new OsvLabel(graphPath, this.getClass().getResource("/output/").getPath().concat("preprocessRanges.bin"), outdir, false);
                tp.labelRevisionGraph();
                Map<String, List<String>> result = tp.getSwhIDToVulnerabilityIds();
                assertEquals("Error for " + rangeFile + " :\n", expected, result);
                tp.close();

        }

        @Test
        public void testNonRegressionPythonDataset() throws IOException, InterruptedException, ClassNotFoundException {

                final String graphPath = this.getClass().getResource("/dataset/").getPath()
                                .concat("2021-03-23-popular-3k-python/")
                                .concat("compressed/graph");

                final String outdir = "NULL";// not needed
                final String vulnRangesPath = this.getClass().getResource("/ranges/").getPath()
                                .concat("full-range/vuln_ranges.json");
                final String expectedPath = this.getClass().getResource("/output/").getPath().concat("expected.bin");

                // labeling the graph
                List<VulnerabilityRange> vulns = OsvLabelAbstract.deserializeVulnRanges(vulnRangesPath);
                BinIO.storeObject(vulns, this.getClass().getResource("/output").getPath().concat("preprocess.bin"));
                final OsvLabel tp = new OsvLabel(graphPath, this.getClass().getResource("/output").getPath().concat("preprocess.bin"), outdir, false);
                tp.labelRevisionGraph();

                // getting the expected result and comparing it to the current result

                File f = new File(expectedPath);
                if (f.exists() && !f.isDirectory()) {
                        FileInputStream fis = new FileInputStream(expectedPath);
                        ObjectInputStream ois = new ObjectInputStream(fis);
                        try {
                                AffectedRevisions expected = (AffectedRevisions) ois.readObject();
                                System.out.println(tp.affectedRevisions.getMap() + "\n" + expected.getMap());
                                assertEquals(tp.affectedRevisions.getMap(), expected.getMap());
                        } catch (ClassNotFoundException e) {
                                e.printStackTrace();
                        }
                        ois.close();
                }

        }

        /**
         * argument provider for the simple tests of osvLabl
         * 
         * @see /docs/OSV-TESTS/test-cases.md
         * @return
         */
        private static Stream<Arguments> argumentProvider() {
                return Stream.of(
                                // introduced fixed
                                Arguments.of(
                                                "linear/", "introduced-fixed/Linear.json",
                                                Map.of(
                                                                "swh:1:rev:0000000000000000000000000000000000000301",
                                                                List.of("CVE-2016-2105"),
                                                                "swh:1:rev:0000000000000000000000000000000000000302",
                                                                List.of("CVE-2016-2105"))),
                                Arguments.of(
                                                "checkout/", "introduced-fixed/Checkout.json",
                                                Map.of(
                                                                "swh:1:rev:0000000000000000000000000000000000000301",
                                                                List.of("CVE-2016-2105"),
                                                                "swh:1:rev:0000000000000000000000000000000000000302",
                                                                List.of("CVE-2016-2105"),
                                                                "swh:1:rev:0000000000000000000000000000000000000304",
                                                                List.of("CVE-2016-2105"))),
                                Arguments.of(
                                                "merge/", "introduced-fixed/Merge.json",
                                                Map.of(
                                                                "swh:1:rev:0000000000000000000000000000000000000301",
                                                                List.of("CVE-2016-2105"),
                                                                "swh:1:rev:0000000000000000000000000000000000000303",
                                                                List.of("CVE-2016-2105"))),
                                Arguments.of(
                                                "linear4/", "introduced-fixed/Linear4.json",
                                                Map.of(
                                                                "swh:1:rev:0000000000000000000000000000000000000301",
                                                                List.of("CVE-2016-2105"),
                                                                "swh:1:rev:0000000000000000000000000000000000000303",
                                                                List.of("CVE-2016-2105"))),
                                Arguments.of(
                                                "merge4/", "introduced-fixed/Merge4.json",
                                                Map.of(
                                                                "swh:1:rev:0000000000000000000000000000000000000301",
                                                                List.of("CVE-2016-2105"),
                                                                "swh:1:rev:0000000000000000000000000000000000000303",
                                                                List.of("CVE-2016-2105"))),

                               /*  Arguments.of(
                                                "merge4/", "introduced-fixed/MergeFixPropagation.json",
                                                Map.of(
                                                                "swh:1:rev:0000000000000000000000000000000000000301",
                                                                List.of("CVE-2016-2105"))), */
                                // 
                                // introduced-limit
                                Arguments.of(
                                                "linear/", "introduced-limit/Linear.json",
                                                Map.of(
                                                                "swh:1:rev:0000000000000000000000000000000000000301",
                                                                List.of("CVE-2016-2105"),
                                                                "swh:1:rev:0000000000000000000000000000000000000302",
                                                                List.of("CVE-2016-2105"))),
                                Arguments.of(
                                                "checkout/", "introduced-limit/Checkout.json",
                                                Map.of(
                                                                "swh:1:rev:0000000000000000000000000000000000000301",
                                                                List.of("CVE-2016-2105"),
                                                                "swh:1:rev:0000000000000000000000000000000000000302",
                                                                List.of("CVE-2016-2105"))),
                                Arguments.of(
                                                "merge/", "introduced-limit/Merge.json",
                                                Map.of(
                                                                "swh:1:rev:0000000000000000000000000000000000000301",
                                                                List.of("CVE-2016-2105"),
                                                                "swh:1:rev:0000000000000000000000000000000000000303",
                                                                List.of("CVE-2016-2105"))),
                                Arguments.of(
                                                "linear4/", "introduced-limit/Linear4.json",
                                                Map.of(
                                                                "swh:1:rev:0000000000000000000000000000000000000301",
                                                                List.of("CVE-2016-2105"),
                                                                "swh:1:rev:0000000000000000000000000000000000000303",
                                                                List.of("CVE-2016-2105"))),
                                // introduced-last_affected
                                Arguments.of(
                                                "linear/", "introduced-last_affected/Linear.json",
                                                Map.of(
                                                                "swh:1:rev:0000000000000000000000000000000000000301",
                                                                List.of("CVE-2016-2105"),
                                                                "swh:1:rev:0000000000000000000000000000000000000302",
                                                                List.of("CVE-2016-2105"),
                                                                "swh:1:rev:0000000000000000000000000000000000000303",
                                                                List.of("CVE-2016-2105"))),
                                Arguments.of(
                                                "checkout/", "introduced-last_affected/Checkout.json",
                                                Map.of(
                                                                "swh:1:rev:0000000000000000000000000000000000000301",
                                                                List.of("CVE-2016-2105"),
                                                                "swh:1:rev:0000000000000000000000000000000000000302",
                                                                List.of("CVE-2016-2105"),
                                                                "swh:1:rev:0000000000000000000000000000000000000303",
                                                                List.of("CVE-2016-2105"),
                                                                "swh:1:rev:0000000000000000000000000000000000000304",
                                                                List.of("CVE-2016-2105"))),
                                Arguments.of(
                                                "merge/", "introduced-last_affected/Merge.json",
                                                Map.of(
                                                                "swh:1:rev:0000000000000000000000000000000000000301",
                                                                List.of("CVE-2016-2105"),
                                                                "swh:1:rev:0000000000000000000000000000000000000303",
                                                                List.of("CVE-2016-2105"),
                                                                "swh:1:rev:0000000000000000000000000000000000000304",
                                                                List.of("CVE-2016-2105"))),
                                Arguments.of(
                                                "linear4/", "introduced-last_affected/Linear4.json",
                                                Map.of(
                                                                "swh:1:rev:0000000000000000000000000000000000000301",
                                                                List.of("CVE-2016-2105"),
                                                                "swh:1:rev:0000000000000000000000000000000000000302",
                                                                List.of("CVE-2016-2105"),
                                                                "swh:1:rev:0000000000000000000000000000000000000303",
                                                                List.of("CVE-2016-2105"),
                                                                "swh:1:rev:0000000000000000000000000000000000000304",
                                                                List.of("CVE-2016-2105"))),
                                // introduced-limit-fixed
                                Arguments.of(
                                                "linear/", "introduced-limit-fixed/Linear_LF.json",
                                                Map.of(
                                                                "swh:1:rev:0000000000000000000000000000000000000301",
                                                                List.of("CVE-2016-2105"))),
                                Arguments.of(
                                                "linear/", "introduced-limit-fixed/Linear_FL.json",
                                                Map.of(
                                                                "swh:1:rev:0000000000000000000000000000000000000301",
                                                                List.of("CVE-2016-2105"))),
                                Arguments.of(
                                                "checkout-early/", "introduced-limit-fixed/Linear_FL.json",
                                                Map.of(
                                                                "swh:1:rev:0000000000000000000000000000000000000301",
                                                                List.of("CVE-2016-2105")))

                );
        }

}
