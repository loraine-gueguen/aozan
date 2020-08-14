package fr.ens.biologie.genomique.aozan.aozan3.recipe;

import static java.util.Objects.requireNonNull;

import java.util.Collections;
import java.util.Set;

import fr.ens.biologie.genomique.aozan.aozan3.Aozan3Exception;
import fr.ens.biologie.genomique.aozan.aozan3.Configuration;
import fr.ens.biologie.genomique.aozan.aozan3.DataStorage;
import fr.ens.biologie.genomique.aozan.aozan3.RunConfiguration;
import fr.ens.biologie.genomique.aozan.aozan3.RunData;
import fr.ens.biologie.genomique.aozan.aozan3.dataprocessor.DataProcessor;
import fr.ens.biologie.genomique.aozan.aozan3.dataprocessor.DataProcessor.ProcessResult;
import fr.ens.biologie.genomique.aozan.aozan3.dataprocessor.DataProcessorService;
import fr.ens.biologie.genomique.aozan.aozan3.dataprocessor.InputData;
import fr.ens.biologie.genomique.aozan.aozan3.datatypefilter.DataTypeFilter;
import fr.ens.biologie.genomique.aozan.aozan3.log.AozanLogger;
import fr.ens.biologie.genomique.aozan.aozan3.runconfigurationprovider.EmptyRunConfigurationProvider;
import fr.ens.biologie.genomique.aozan.aozan3.runconfigurationprovider.RunConfigurationProvider;

/**
 * This class define a step of the recipe.
 * @author Laurent Jourdren
 * @since 3.0
 */
public class Step {

  private final String name;
  private final String processorName;
  private final String sourceName;
  private final String sinkName;
  private final Configuration conf;
  private final RunConfigurationProvider runConfProvider;
  // private String outputNameConf;

  private transient DataProcessor processor;
  private boolean initialized;

  private final AozanLogger logger;
  private final DataStorageRegistry storages;

  /**
   * Get the name of the step.
   * @return the name of the step
   */
  public String getName() {
    return this.name;
  }

  /**
   * Initialize the step.
   * @throws Aozan3Exception if an error occurs while initializing the step
   */
  public void init() throws Aozan3Exception {

    // Check if step has not been already initialized
    if (this.initialized) {
      throw new IllegalStateException();
    }

    DataStorage sink = this.storages.get(sinkName);

    Configuration processorConf = new Configuration(this.conf);
    processorConf.set("output.storage", sink.toJson());

    this.logger.info("Looking for processor: " + this.processorName);

    this.processor =
        DataProcessorService.getInstance().newService(this.processorName);

    this.logger.info("Found "
        + this.processor.getClass().getName() + "  for processor "
        + this.processorName);

    this.logger.info("Initialize processor " + this.processorName);
    this.processor.init(processorConf, this.logger);
    this.logger
        .info("Initialization of processor " + this.processorName + " done");

    this.initialized = true;
  }

  /**
   * Get the input requirements of the processor.
   * @return a set with input requirements
   */
  public Set<DataTypeFilter> getInputRequirements() {

    Set<DataTypeFilter> result =

        this.processor.getInputRequirements();

    if (result == null) {
      return Collections.emptySet();
    }

    return result;
  }

  /**
   * Process input data.
   * @param inputData input data to process
   * @return a process result
   * @throws Aozan3Exception if an error occurs while executing the step
   */
  public ProcessResult process(InputData inputData) throws Aozan3Exception {

    // Check if step has been initialized
    if (!this.initialized) {
      throw new IllegalStateException();
    }

    RunData inputRunData = inputData.getLastRunData();

    this.logger.info(inputRunData, "Get run configuration for the run");

    // Get run configuration
    RunConfiguration runConf =
        this.runConfProvider.getRunConfiguration(inputRunData);

    this.logger.info(inputRunData,
        "Launch processor " + this.processorName + " for the run");

    // Launch processor
    ProcessResult result = this.processor.process(inputData, runConf);

    this.logger.info(inputRunData,
        "End of processor " + this.processorName + " for the run");

    return result;
  }

  //
  // Constructor
  //

  /**
   * Constructor.
   * @param recipe recipe of the step
   * @param stepName step name
   * @param processorName name of the processor
   * @param sourceName source name
   * @param sinkName sink name
   * @param conf step configuration
   * @param runConfProvider run configuration to use
   */
  public Step(Recipe recipe, String stepName, String processorName,
      String sourceName, String sinkName, Configuration conf,
      RunConfigurationProvider runConfProvider) {

    requireNonNull(recipe);
    requireNonNull(stepName);
    requireNonNull(processorName);
    requireNonNull(sourceName);
    requireNonNull(sinkName);
    requireNonNull(stepName);

    this.name = stepName;
    this.processorName = processorName;
    this.sourceName = sourceName;
    this.sinkName = sinkName;
    this.runConfProvider = runConfProvider != null
        ? runConfProvider : new EmptyRunConfigurationProvider();

    this.logger = recipe.getLogger();
    this.storages = recipe.getStorages();

    // The default configuration of the step is the recipe configuration
    this.conf = recipe.getConfiguration();

    // Add if exists additional configuration
    if (conf != null) {
      this.conf.set(conf);
    }

    // Add the step to the recipe
    recipe.addStep(this);
  }
}
