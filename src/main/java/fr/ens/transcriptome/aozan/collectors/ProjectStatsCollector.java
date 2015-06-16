/*
 *                  Aozan development code
 *
 * This code may be freely distributed and modified under the
 * terms of the GNU General Public License version 3 or later 
 * and CeCILL. This should be distributed with the code. If you 
 * do not have a copy, see:
 *
 *      http://www.gnu.org/licenses/gpl-3.0-standalone.html
 *      http://www.cecill.info/licences/Licence_CeCILL_V2-en.html
 *
 * Copyright for this code is held jointly by the Genomic platform
 * of the Institut de Biologie de l'École Normale Supérieure and
 * the individual authors. These should be listed in @author doc
 * comments.
 *
 * For more information on the Aozan project and its aims,
 * or to join the Aozan Google group, visit the home page at:
 *
 *      http://www.transcriptome.ens.fr/aozan
 *
 */
package fr.ens.transcriptome.aozan.collectors;

import java.io.File;
import java.io.FileFilter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.logging.Logger;

import com.google.common.base.Joiner;
import com.google.common.base.Splitter;
import com.google.common.collect.Lists;

import fr.ens.transcriptome.aozan.AozanException;
import fr.ens.transcriptome.aozan.Common;
import fr.ens.transcriptome.aozan.Globals;
import fr.ens.transcriptome.aozan.QC;
import fr.ens.transcriptome.aozan.RunData;
import fr.ens.transcriptome.aozan.Settings;
import fr.ens.transcriptome.aozan.fastqscreen.FastqScreenProjectReport;
import fr.ens.transcriptome.aozan.util.StatisticsUtils;
import fr.ens.transcriptome.aozan.util.StringUtils;
import fr.ens.transcriptome.eoulsan.EoulsanRuntimeException;

/**
 * The class define a collector object that compile data on project from samples
 * data generated by other collector especially FastqScreen collector to build a
 * detection contaminant report on a project which compile result on samples.
 * @author Sandrine Perrin
 * @since 1.4
 */
public class ProjectStatsCollector implements Collector {

  /** Logger. */
  private static final Logger LOGGER = Common.getLogger();

  /** Collector name. */
  public static final String COLLECTOR_NAME = "projectstats";

  /** Collector prefix for updating rundata */
  public static final String COLLECTOR_PREFIX = "projectstats.";

  /** Default contaminantion percent threshold. */
  private static final double DEFAULT_CONTAMINATION_PERCENT_THRESHOLD = 0.10;

  /** Report directory. */
  private String reportDir;

  /** Stylesheet xsl file. */
  private File fastqscreenXSLFile;

  /** Contamination threshold. */
  private double contaminationThreshold;

  private boolean undeterminedIndexesCollectorSelected = false;
  private boolean fastqScreenCollectorSelected = false;

  @Override
  public String getName() {
    return COLLECTOR_NAME;
  }

  @Override
  public List<String> getCollectorsNamesRequiered() {

    // UndeterminedIndexesCollector and FastqScreenCollector is optional for
    // this collector.
    // Use their data only if a test use them.

    return Lists.newArrayList(RunInfoCollector.COLLECTOR_NAME,
        DesignCollector.COLLECTOR_NAME,
        FlowcellDemuxSummaryCollector.COLLECTOR_NAME);
  }

  @Override
  public void configure(final Properties properties) {

    // Set control quality directory
    this.reportDir = properties.getProperty(QC.QC_OUTPUT_DIR);

    // Set stylesheet file to build project report
    try {
      final String filename =
          properties
              .getProperty(Settings.QC_CONF_FASTQSCREEN_PROJECT_XSL_FILE_KEY);
      if (new File(filename).exists()) {
        this.fastqscreenXSLFile = new File(filename);
      }
    } catch (final Exception e) {
      // Call default xsl file
      this.fastqscreenXSLFile = null;
    }

    // Extract threshold from property
    final String threshod =
        properties
            .getProperty(Settings.QC_CONF_FASTQSCREEN_PERCENT_CONTAMINATION_THRESHOLD_KEY);

    // Set the contaminant threshold
    if (threshod == null || threshod.isEmpty()) {
      // Use default threshold
      this.contaminationThreshold = DEFAULT_CONTAMINATION_PERCENT_THRESHOLD;
    } else {
      try {
        this.contaminationThreshold = Double.parseDouble(threshod);
      } catch (Exception e) {
        this.contaminationThreshold = DEFAULT_CONTAMINATION_PERCENT_THRESHOLD;
      }
    }

    // Check optional collector selected
    final List<String> collectorNames =
        StringUtils.COMMA_SPLITTER.splitToList(properties
            .getProperty(QC.QC_COLLECTOR_NAMES));

    undeterminedIndexesCollectorSelected =
        collectorNames.contains(UndeterminedIndexesCollector.COLLECTOR_NAME);

    fastqScreenCollectorSelected =
        collectorNames.contains(FastqScreenCollector.COLLECTOR_NAME);

  }

  @Override
  public void clear() {

  }

  @Override
  public void collect(RunData data) throws AozanException {

    // TODO
    // Save rundata in tmp
    try {
      data.createRunDataFile("/tmp/rundata.txt");
    } catch (IOException e1) {
      // TODO Auto-generated catch block
      e1.printStackTrace();
    }

    // Parse FastqSample to build list Project
    final List<ProjectStat> projects = extractProjectsStats(data);

    // Collect projects statistics in rundata
    for (ProjectStat project : projects) {
      data.put(project.createRunDataProject());
    }

    try {
      // Build FastqScreen project report html
      createProjectsReport(projects);
    } catch (IOException e) {
      throw new AozanException(e);
    }
  }

  /**
   * Creates the projects report.
   * @param projects the projects
   * @throws AozanException the Aozan exception
   * @throws IOException if a error occurs when create report HTML.
   */
  private void createProjectsReport(final List<ProjectStat> projects)
      throws AozanException, IOException {

    // Check FastqScreen collected
    if (!isFastqScreenCollectorSelected()) {
      // No selected, no data to create project report
      return;
    }

    for (ProjectStat p : projects) {
      final FastqScreenProjectReport fpr =
          new FastqScreenProjectReport(p, fastqscreenXSLFile);

      fpr.createReport(p.getReportHtmlFile());

    }

  }

  /**
   * Extract projects stats.
   * @param data the data.
   * @return the list of ProjectStats sorted by project name.
   * @throws AozanException if an error occurs when initialize a ProjectStats
   *           object or when a sample can not be add to ProjectStats.
   */
  private List<ProjectStat> extractProjectsStats(final RunData data)
      throws AozanException {

    final int laneCount = data.getLaneCount();

    // Initialization ProjectStats with the project name
    final Map<String, ProjectStat> projects = initMap(data);

    // Add projects name
    for (int lane = 1; lane <= laneCount; lane++) {

      // Parse all samples in lane
      for (String sampleName : data.getSamplesNameListInLane(lane)) {

        // Extract project name related to sample name
        final String projectName = data.getProjectSample(lane, sampleName);

        // Save new sample in related project
        projects.get(projectName).addSample(lane, sampleName);
      }
    }

    // Sorted list project
    final List<ProjectStat> projectsSorted =
        new ArrayList<ProjectStatsCollector.ProjectStat>(projects.values());

    Collections.sort(projectsSorted);

    return Collections.unmodifiableList(projectsSorted);
  }

  /**
   * Initialization ProjectStat object.
   * @param data the data
   * @return the map
   * @throws AozanException the aozan exception
   */
  private Map<String, ProjectStat> initMap(final RunData data)
      throws AozanException {

    final Map<String, ProjectStat> projects =
        new HashMap<String, ProjectStatsCollector.ProjectStat>();

    // Extract projects names from run data
    final List<String> projectsName = data.getProjectsNameList();

    // Add projects
    for (String projectName : projectsName) {
      projects.put(projectName, new ProjectStat(data, projectName));
    }

    return Collections.unmodifiableMap(projects);
  }

  /**
   * Checks if is undetermined indexes collector selected.
   * @return true, if is undetermined indexes collector selected
   */
  boolean isUndeterminedIndexesCollectorSelected() {
    return this.undeterminedIndexesCollectorSelected;
  }

  /**
   * Checks if is fastqscreen collector selected.
   * @return true, if is fastqscreen collector selected
   */
  boolean isFastqScreenCollectorSelected() {
    return this.fastqScreenCollectorSelected;
  }

  //
  // Internal class
  //

  /**
   * The class define a ProjectStat object which collect data on a project to
   * update run data.
   * @author Sandrine Perrin
   * @since 1.4
   */
  public final class ProjectStat implements Comparable<ProjectStat> {

    /** Default genome value */
    private static final String DEFAULT_GENOME = "No genome.";

    /** Default read value, manage only read 1 */
    private static final int READ = 1;

    /** Run data */
    private final RunData data;

    /** Project name. */
    private final String projectName;

    /** Report samples on detection contaminant */
    private final List<File> fastqscreenReportSamples;

    /** Genomes. */
    private final Set<String> genomes;

    /** Samples, with technical replicats */
    private final List<String> samples;

    /** Lanes in run for project */
    private final Set<Integer> lanes;

    // Compile demultiplexing data on all samples
    /** Raw cluster count samples. */
    private List<Integer> rawClusterSamples;

    /** PF cluster count samples. */
    private List<Integer> pfClusterSamples;

    /**
     * Percent mapped contamination samples, value related to mapped read on
     * data set genomes contaminant setting.
     */
    private List<Double> mappedContaminationPercentSamples;

    /** Sample count. */
    private int sampleCount = 0;

    /** Project is indexed */
    private boolean isIndexed;

    /** Raw cluster recovery sum. */
    private int rawClusterRecoverySum = 0;

    /** Cluster recovery sum. */
    private int pfClusterRecoverySum = 0;

    /** Project directory output. */
    private File projectDir;

    /** Data compile in run data */
    private boolean asCompiledData = false;

    @Override
    public int compareTo(final ProjectStat that) {

      // Compare on project name
      return that.projectName.toLowerCase(Globals.DEFAULT_LOCALE).compareTo(
          this.projectName.toLowerCase(Globals.DEFAULT_LOCALE));
    }

    /**
     * Creates the run data project.
     * @return the run data.
     * @throws AozanException if run data object has already be create.
     */
    public RunData createRunDataProject() throws AozanException {

      if (asCompiledData) {
        throw new AozanException("Run data always updated for project "
            + projectName + ".");
      }

      final RunData data = new RunData();
      StatisticsUtils stats = null;

      data.put(getPrefixRunData() + ".lanes", Joiner.on(",").join(this.lanes));
      data.put(getPrefixRunData() + ".genomes.ref",
          Joiner.on(",").join(getGenomes()));
      data.put(getPrefixRunData() + ".samples.count", samples.size());
      data.put(getPrefixRunData() + ".isindexed", isIndexed);

      // Compile data on raw cluster
      stats = new StatisticsUtils(this.rawClusterSamples);

      data.put(getPrefixRunData() + ".raw.cluster.sum", stats.getSumToInteger());
      data.put(getPrefixRunData() + ".raw.cluster.min", stats.getMin()
          .intValue());
      data.put(getPrefixRunData() + ".raw.cluster.max", stats.getMax()
          .intValue());

      // Compile data on raw cluster
      stats = new StatisticsUtils(this.pfClusterSamples);

      data.put(getPrefixRunData() + ".pf.cluster.sum", stats.getSumToInteger());
      data.put(getPrefixRunData() + ".pf.cluster.min", stats.getMin()
          .intValue());
      data.put(getPrefixRunData() + ".pf.cluster.max", stats.getMax()
          .intValue());

      addConditionalRundata(data);

      asCompiledData = true;

      return data;
    }

    private void addConditionalRundata(final RunData data) {

      // Check collector is selected
      if (isUndeterminedIndexesCollectorSelected()) {
        // Compile data on recoverable cluster
        data.put(getPrefixRunData() + ".raw.cluster.recovery.sum",
            rawClusterRecoverySum);
        data.put(getPrefixRunData() + ".pf.cluster.recovery.sum",
            pfClusterRecoverySum);
      }

      // Check collector is selected
      if (isFastqScreenCollectorSelected()) {
        // Compile data on detection contamination
        data.put(getPrefixRunData()
            + ".samples.exceeded.contamination.threshold.count",
            getSamplesWithContaminationCount());
      }
    }

    /**
     * Adds the sample.
     * @param lane the lane in run
     * @param sample the sample name
     * @throws AozanException if run data object has already be create.
     */
    public void addSample(final int lane, final String sample)
        throws AozanException {

      if (asCompiledData) {
        throw new AozanException("Can not add new sample ("
            + sample + ")for projet " + projectName
            + ".Data always compile to updata run data.");
      }

      this.lanes.add(lane);
      this.sampleCount++;

      this.isIndexed = this.data.isLaneIndexed(lane);

      // Extract raw cluster
      this.rawClusterSamples.add(this.data.getSampleRawClusterCount(lane, READ,
          sample));

      // Extract pf cluster
      this.pfClusterSamples.add(this.data.getSamplePFClusterCount(lane, READ,
          sample));

      computeConditionalRundata(lane, sample);

      this.samples.add(sample);

      // Extract from samplesheet file
      this.genomes.add(data.getSampleGenome(lane, sample));

    }

    /**
     * Compute conditional rundata according to collector selected.
     * UndeterminedIndexesCollector and FastqScreenCollector is optional for
     * this collector.
     * @param lane the lane
     * @param sample the sample
     */
    private void computeConditionalRundata(final int lane, final String sample) {

      // Check collector is selected
      if (isUndeterminedIndexesCollectorSelected()) {

        // Check if lane is indexed
        if (this.data.isLaneIndexed(lane)) {
          this.rawClusterRecoverySum +=
              this.data.getSampleRawClusterRecoveryCount(lane, sample);

          this.pfClusterRecoverySum +=
              this.data.getSamplePFClusterRecoveryCount(lane, sample);
        }
      }

      // Check collector is selected
      if (isFastqScreenCollectorSelected()) {

        // TODO
        LOGGER.severe("sample "
            + sample + " in lane " + lane + " data read size "
            + this.data.size());

        this.mappedContaminationPercentSamples.add(this.data
            .getPercentMappedReadOnContaminationSample(lane, sample, READ));
      }

    }

    /**
     * Extract fastqscreen xml report create for samples.
     * @return the list of xml file used to create project report.
     * @throws AozanException if the output project directory does not exist.
     */
    private List<File> extractFastqscreenReport() throws AozanException {

      if (!projectDir.exists())
        throw new AozanException("Project directory does not exist "
            + projectDir.getAbsolutePath());

      // Exrtact in project directy all fastqscreen report xml
      final List<File> reports =
          Arrays.asList(projectDir.listFiles(new FileFilter() {

            @Override
            public boolean accept(final File pathname) {
              return pathname.length() > 0
                  && pathname.getName().endsWith("-fastqscreen.xml");
            }
          }));

      // Sort by filename
      Collections.sort(reports);

      return Collections.unmodifiableList(reports);
    }

    //
    // Getter
    //

    /**
     * Gets the samples with contamination count.
     * @return the samples with contamination count
     */
    private String getSamplesWithContaminationCount() {
      int count = 0;

      for (double percent : this.mappedContaminationPercentSamples) {

        if (percent >= contaminationThreshold)
          count++;
      }

      return count + "";
    }

    /**
     * Gets the genomes.
     * @return the genomes
     */
    private Set<String> getGenomes() {

      if (this.genomes.isEmpty())
        return Collections.singleton(DEFAULT_GENOME);

      return this.genomes;
    }

    /**
     * Gets the report html file.
     * @return the report html file
     */
    public File getReportHtmlFile() {

      return new File(reportDir + "/Project_" + this.projectName,
          this.projectName + "-fastqscreen.html");
    }

    /**
     * Gets the prefix run data.
     * @return the prefix run data
     */
    private String getPrefixRunData() {
      return COLLECTOR_PREFIX + projectName;
    }

    /**
     * Gets the samples.
     * @return the samples
     */
    public List<String> getSamples() {
      return samples;
    }

    /**
     * Gets the fastq screen report.
     * @return the fastq screen report
     */
    public List<File> getFastqScreenReport() {

      if (!isFastqScreenCollectorSelected())
        throw new UnsupportedOperationException();

      if (this.fastqscreenReportSamples.size() != this.sampleCount)
        throw new EoulsanRuntimeException("In project "
            + projectName + " samples count " + sampleCount
            + " incompatible with fastqscreen report found "
            + this.fastqscreenReportSamples.size());

      return Collections.unmodifiableList(this.fastqscreenReportSamples);
    }

    /**
     * Gets the project name.
     * @return the project name
     */
    public String getProjectName() {
      return this.projectName;
    }

    @Override
    public String toString() {
      return "ProjectStat [data="
          + data + ", projectName=" + projectName
          + ", fastqscreenReportSamples=" + fastqscreenReportSamples
          + ", genomes=" + genomes + ", samples=" + samples + ", projectDir="
          + projectDir + "]";
    }

    //
    // Constructor
    //
    /**
     * Instantiates a new project stat.
     * @param runData the run data
     * @param projectName the project name
     * @throws AozanException if an error occurs when listing source fastqscreen
     *           xml report file.
     */
    public ProjectStat(final RunData runData, final String projectName)
        throws AozanException {
      this.data = runData;
      this.projectName = projectName;

      this.genomes = new LinkedHashSet<>();
      this.lanes = new LinkedHashSet<>();
      this.samples = new ArrayList<>();

      // Compile demultiplexing data
      this.rawClusterSamples = new ArrayList<>();
      this.pfClusterSamples = new ArrayList<>();
      this.mappedContaminationPercentSamples = new ArrayList<>();

      this.projectDir = new File(reportDir + "/Project_" + this.projectName);

      if (isFastqScreenCollectorSelected()) {
        this.fastqscreenReportSamples = extractFastqscreenReport();
      } else {
        this.fastqscreenReportSamples = null;
      }
    }

  }

}
