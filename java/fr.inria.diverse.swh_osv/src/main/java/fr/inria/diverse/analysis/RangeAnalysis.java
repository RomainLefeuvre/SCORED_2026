package fr.inria.diverse.analysis;

import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.martiansoftware.jsap.FlaggedOption;
import com.martiansoftware.jsap.JSAP;
import com.martiansoftware.jsap.JSAPException;
import com.martiansoftware.jsap.JSAPResult;
import com.martiansoftware.jsap.Parameter;
import com.martiansoftware.jsap.SimpleJSAP;

import fr.inria.diverse.Utils.ToolBox;
import fr.inria.diverse.model.EventType;
import fr.inria.diverse.model.SwhVulnerabilityRange;
import it.unimi.dsi.fastutil.io.BinIO;

/**
 * The purpose of this script is to analyse the difference between the original
 * ranges and the ones extended by cherry picking
 */
public class RangeAnalysis {
  private ArrayList<SwhVulnerabilityRange> swhVulnerabilityRanges;
  private ArrayList<SwhVulnerabilityRange> swhVulnerabilityRangesPreprocessed;
  private Map<String, Double> oldAverages;
  private Map<String, Double> newAverages;
  private Map<String, Long> oldTotal;
  private Map<String, Long> newTotal;

  private String statisticsOutputPath;

  public RangeAnalysis(String vulnRangesPath, String vulnPreprocessRangesPath, String statisticsOutputPath)
      throws ClassNotFoundException, IOException {
    this.swhVulnerabilityRanges = (ArrayList<SwhVulnerabilityRange>) BinIO.loadObject(vulnRangesPath);
    this.swhVulnerabilityRangesPreprocessed = (ArrayList<SwhVulnerabilityRange>) BinIO
        .loadObject(vulnPreprocessRangesPath);

    this.statisticsOutputPath = statisticsOutputPath;
  }

  public RangeAnalysis analyse() {
    ArrayList<EventType> eventTypes = new ArrayList<>();
    eventTypes.add(EventType.INTRODUCED);
    eventTypes.add(EventType.FIXED);
    eventTypes.add(EventType.LAST_AFFECTED);
    eventTypes.add(EventType.LIMIT);

    this.oldAverages = new HashMap<>();
    this.newAverages = new HashMap<>();
    this.oldTotal = new HashMap<>();
    this.newTotal = new HashMap<>();

    for (EventType eventType : eventTypes) {
      this.oldAverages.put(eventType.name(), getAverageNumberEvents(swhVulnerabilityRanges, eventType));
      this.newAverages.put(eventType.name(), getAverageNumberEvents(swhVulnerabilityRangesPreprocessed, eventType));
    }

    for (EventType eventType : eventTypes) {
      this.oldTotal.put(eventType.name(), getTotalNumberEvents(swhVulnerabilityRanges, eventType));
      this.newTotal.put(eventType.name(), getTotalNumberEvents(swhVulnerabilityRangesPreprocessed, eventType));
    }

    return this;
  }

  private Double getAverageNumberEvents(ArrayList<SwhVulnerabilityRange> range, EventType eventType) {
    return range.stream().mapToLong(vuln -> vuln.getRevisionByEvent(eventType).size()).average().getAsDouble();
  }
  private Long getTotalNumberEvents(ArrayList<SwhVulnerabilityRange> range, EventType eventType) {
    return range.stream().mapToLong(vuln -> vuln.getRevisionByEvent(eventType).size()).sum();
  }

  public void jsonExport() throws IOException {
    ToolBox.createParentIfNeeded(statisticsOutputPath);
    Gson gson = new Gson();
    JsonObject statisticsValues = new JsonObject();

    for (EventType eventType : EventType.values()) {
      statisticsValues.addProperty("old_average_"+eventType.name(), this.oldAverages.get(eventType.name()));
      statisticsValues.addProperty("new_average_"+eventType.name(), this.newAverages.get(eventType.name()));
      statisticsValues.addProperty("old_total_"+eventType.name(), this.oldTotal.get(eventType.name()));
      statisticsValues.addProperty("new_total_"+eventType.name(), this.newTotal.get(eventType.name()));
    }
    PrintWriter pw = new PrintWriter(this.statisticsOutputPath);

    pw.println(gson.toJson(statisticsValues));
    pw.close();
  }

  protected static JSAPResult parse_args(final String[] args) {
    JSAPResult config = null;
    try {
      final SimpleJSAP jsap = new SimpleJSAP(VulnerableRepoAnalysis.class.getName(),
          "Computes a map of range to origin containing an introduced commit",
          new Parameter[] {
              new FlaggedOption("vulnRangesPath", JSAP.STRING_PARSER, JSAP.NO_DEFAULT, JSAP.REQUIRED, 'v',
                  "vulnRangesPath", "Osv original Range list => the file in .cache containing the ranges with the nodeids"),
              new FlaggedOption("vulnPreprocessRangesPath", JSAP.STRING_PARSER, JSAP.NO_DEFAULT, JSAP.REQUIRED, 'p',
                  "vulnPreprocessRangesPath", "Osv preprocessed Range list"),
              new FlaggedOption("statisticsOutputPath", JSAP.STRING_PARSER, JSAP.NO_DEFAULT, JSAP.REQUIRED, 's',
                  "statisticsOutputPath", "Output path for the statistics")
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

  public static void main(final String[] args) throws ClassNotFoundException, IOException {
    final JSAPResult config = parse_args(args);
    final String vulnRangesPath = config.getString("vulnRangesPath");
    final String vulnPreprocessRangesPath = config.getString("vulnPreprocessRangesPath");
    final String statisticsOutputPath = config.getString("statisticsOutputPath");

    new RangeAnalysis(vulnRangesPath, vulnPreprocessRangesPath, statisticsOutputPath).analyse().jsonExport();
  }

}
