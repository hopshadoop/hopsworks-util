package io.hops.util.featurestore.ops.write_ops;

import io.hops.util.Constants;
import io.hops.util.FeaturestoreRestClient;
import io.hops.util.Hops;
import io.hops.util.exceptions.DataframeIsEmpty;
import io.hops.util.exceptions.FeaturegroupCreationError;
import io.hops.util.exceptions.FeaturegroupDoesNotExistError;
import io.hops.util.exceptions.FeaturestoreNotFound;
import io.hops.util.exceptions.HiveNotEnabled;
import io.hops.util.exceptions.InvalidPrimaryKeyForFeaturegroup;
import io.hops.util.exceptions.JWTNotFoundException;
import io.hops.util.exceptions.OnlineFeaturestoreNotEnabled;
import io.hops.util.exceptions.OnlineFeaturestorePasswordNotFound;
import io.hops.util.exceptions.OnlineFeaturestoreUserNotFound;
import io.hops.util.exceptions.StorageConnectorDoesNotExistError;
import io.hops.util.featurestore.FeaturestoreHelper;
import io.hops.util.featurestore.dtos.app.FeaturestoreMetadataDTO;
import io.hops.util.featurestore.dtos.feature.FeatureDTO;
import io.hops.util.featurestore.dtos.featuregroup.CachedFeaturegroupDTO;
import io.hops.util.featurestore.dtos.featuregroup.FeaturegroupDTO;
import io.hops.util.featurestore.dtos.featuregroup.OnDemandFeaturegroupDTO;
import io.hops.util.featurestore.dtos.jobs.FeaturestoreJobDTO;
import io.hops.util.featurestore.dtos.storageconnector.FeaturestoreStorageConnectorDTO;
import io.hops.util.featurestore.dtos.storageconnector.FeaturestoreStorageConnectorType;
import io.hops.util.featurestore.ops.FeaturestoreOp;
import org.apache.commons.lang3.StringUtils;
import org.apache.parquet.Strings;
import org.apache.spark.sql.Dataset;
import org.apache.spark.sql.Row;
import org.apache.spark.sql.SparkSession;

import javax.xml.bind.JAXBException;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Builder class for Create-Featuregroup operation on the Hopsworks Featurestore
 */
public class FeaturestoreCreateFeaturegroup extends FeaturestoreOp {
  
  /**
   * Constructor
   *
   * @param name name of the featuregroup to create
   */
  public FeaturestoreCreateFeaturegroup(String name) {
    super(name);
  }
  
  /**
   * Method call to execute read operation
   */
  public Object read() {
    throw new UnsupportedOperationException("read() is not supported on a write operation");
  }

  /**
   * Creates a new feature group in the featurestore
   *
   * @throws DataframeIsEmpty DataframeIsEmpty
   * @throws JAXBException JAXBException
   * @throws InvalidPrimaryKeyForFeaturegroup InvalidPrimaryKeyForFeaturegroup
   * @throws FeaturegroupCreationError FeaturegroupCreationError
   * @throws FeaturestoreNotFound FeaturestoreNotFound
   * @throws JWTNotFoundException JWTNotFounfdException
   * @throws HiveNotEnabled HiveNotEnabled
   * @throws StorageConnectorDoesNotExistError StorageConnectorDoesNotExistError
   * @throws OnlineFeaturestoreUserNotFound OnlineFeaturestoreUserNotFound
   * @throws OnlineFeaturestorePasswordNotFound OnlineFeaturestorePasswordNotFound
   * @throws OnlineFeaturestoreNotEnabled OnlineFeaturestoreNotEnabled\
   * @throws FeaturegroupDoesNotExistError FeaturegroupDoesNotExistError
   */
  public void write() throws JWTNotFoundException, FeaturegroupCreationError,
    FeaturestoreNotFound, JAXBException, InvalidPrimaryKeyForFeaturegroup, HiveNotEnabled, DataframeIsEmpty,
    StorageConnectorDoesNotExistError, OnlineFeaturestoreUserNotFound, OnlineFeaturestorePasswordNotFound,
    OnlineFeaturestoreNotEnabled, FeaturegroupDoesNotExistError {
    if(onDemand) {
      writeOnDemandFeaturegroup();
    } else {
      writeCachedFeaturegroup();
    }
    //Update metadata cache since we created a new feature group
    Hops.updateFeaturestoreMetadataCache().setFeaturestore(featurestore).write();
  }

  /**
   * Creates a new onDemand Featuregroup in teh Feature store
   *
   * @throws StorageConnectorDoesNotExistError StorageConnectorDoesNotExistError
   * @throws FeaturestoreNotFound FeaturestoreNotFound
   * @throws FeaturegroupCreationError FeaturegroupCreationError
   * @throws JWTNotFoundException JWTNotFoundException
   * @throws JAXBException JAXBException
   */
  public void writeOnDemandFeaturegroup() throws StorageConnectorDoesNotExistError, FeaturestoreNotFound,
      FeaturegroupCreationError, JWTNotFoundException, JAXBException {
    if(Strings.isNullOrEmpty(sqlQuery)){
      throw new IllegalArgumentException("SQL Query Cannot be Empty or Null for On-Demand Feature Groups");
    }
    if(Strings.isNullOrEmpty(jdbcConnector)){
      throw new IllegalArgumentException("To create an on-demand feature group you must specify the name of a " +
          "JDBC Storage Connector");
    }
    FeaturestoreMetadataDTO featurestoreMetadata = FeaturestoreHelper.getFeaturestoreMetadataCache();
    FeaturestoreStorageConnectorDTO storageConnectorDTO = FeaturestoreHelper.findStorageConnector(
        featurestoreMetadata.getStorageConnectors(), jdbcConnector);
    if(storageConnectorDTO.getStorageConnectorType() != FeaturestoreStorageConnectorType.JDBC){
      throw new IllegalArgumentException("OnDemand Feature groups can only be linked to JDBC Storage Connectors, " +
          "the provided storage connector is of type: " + storageConnectorDTO.getStorageConnectorType());
    }
    FeaturestoreRestClient.createFeaturegroupRest(groupInputParamsIntoDTO(storageConnectorDTO.getId()),
        FeaturestoreHelper.getFeaturegroupDtoTypeStr(featurestoreMetadata.getSettings(), onDemand));
  }

  /**
   * Creates a new cached feature group in the featurestore
   *
   * @throws DataframeIsEmpty DataframeIsEmpty
   * @throws JAXBException JAXBException
   * @throws InvalidPrimaryKeyForFeaturegroup InvalidPrimaryKeyForFeaturegroup
   * @throws FeaturegroupCreationError FeaturegroupCreationError
   * @throws FeaturestoreNotFound FeaturestoreNotFound
   * @throws JWTNotFoundException JWTNotFounfdException
   * @throws HiveNotEnabled HiveNotEnabled
   * @throws OnlineFeaturestoreUserNotFound OnlineFeaturestoreUserNotFound
   * @throws OnlineFeaturestorePasswordNotFound OnlineFeaturestorePasswordNotFound
   * @throws StorageConnectorDoesNotExistError StorageConnectorDoesNotExistError
   * @throws OnlineFeaturestoreNotEnabled OnlineFeaturestoreNotEnabled
   * @throws FeaturegroupDoesNotExistError FeaturegroupDoesNotExistError
   */
  public void writeCachedFeaturegroup()
    throws DataframeIsEmpty,
    JAXBException, InvalidPrimaryKeyForFeaturegroup, FeaturegroupCreationError, FeaturestoreNotFound,
    JWTNotFoundException, HiveNotEnabled, StorageConnectorDoesNotExistError, OnlineFeaturestoreUserNotFound,
    OnlineFeaturestorePasswordNotFound, OnlineFeaturestoreNotEnabled, FeaturegroupDoesNotExistError {
    if(dataframe == null) {
      throw new IllegalArgumentException("Dataframe to create featuregroup from cannot be null, specify dataframe " +
        "with " +
        ".setDataframe(df)");
    }
    FeaturestoreMetadataDTO featurestoreMetadata = FeaturestoreHelper.getFeaturestoreMetadataCache();
    primaryKey = FeaturestoreHelper.primaryKeyGetOrDefault(primaryKey, dataframe);
    List<FeatureDTO> featuresSchema = FeaturestoreHelper.parseSparkFeaturesSchema(dataframe.schema(), primaryKey,
      partitionBy, online, onlineTypes);
    FeaturestoreHelper.validatePrimaryKey(dataframe, primaryKey);
    FeaturestoreHelper.validateMetadata(name, featuresSchema, description);
    if(!hudi) {
      FeaturestoreRestClient.createFeaturegroupRest(groupInputParamsIntoDTO(featuresSchema),
        FeaturestoreHelper.getFeaturegroupDtoTypeStr(featurestoreMetadata.getSettings(), onDemand));
      if(offline){
        FeaturestoreHelper.insertIntoOfflineFeaturegroup(dataframe, getSpark(), name,
          featurestore, version);
      }
      if(online) {
        FeaturestoreHelper.insertIntoOnlineFeaturegroup(dataframe, name, featurestore, version,
          Constants.SPARK_OVERWRITE_MODE);
      }
    } else {
      Map<String, String> hudiWriteArgs = setupHudiArgs();
      FeaturestoreHelper.writeHudiDataset(dataframe, getSpark(), name, featurestore, version,
        hudiWriteArgs, hudiBasePath, Constants.SPARK_OVERWRITE_MODE);
      new FeaturestoreSyncHiveTable(name).setFeaturestore(featurestore).setDescription(description)
        .setVersion(version).setJobs(jobs).write();
    }
  }
  
  /**
   * Setup the Hudi arguments for doing a bulk insert and creating a new hudi feature group
   *
   * @return the hudi write arguments
   * @throws StorageConnectorDoesNotExistError
   */
  private Map<String, String> setupHudiArgs() throws StorageConnectorDoesNotExistError {
    primaryKey = FeaturestoreHelper.primaryKeyGetOrDefault(primaryKey, dataframe);
    //Add default args
    Map<String, String> hArgs = Constants.HUDI_DEFAULT_ARGS;
    hArgs.put(Constants.HUDI_TABLE_OPERATION, Constants.HUDI_BULK_INSERT);
    hArgs.put(Constants.HUDI_TABLE_NAME, FeaturestoreHelper.getTableName(name, version));
    hArgs.put(Constants.HUDI_RECORD_KEY, primaryKey.get(0));
    hArgs.put(Constants.HIVE_PARTITION_EXTRACTOR_CLASS_OPT_KEY,
      Constants.DEFAULT_HIVE_PARTITION_EXTRACTOR_CLASS_OPT_VAL);
    if(!partitionBy.isEmpty()) {
      hArgs.put(Constants.HUDI_PARTITION_FIELD, StringUtils.join(partitionBy, ","));
      hArgs.put(Constants.HUDI_PRECOMBINE_FIELD, StringUtils.join(partitionBy, ","));
      hArgs.put(Constants.HUDI_HIVE_SYNC_PARTITION_FIELDS, StringUtils.join(partitionBy, ","));
    }
    hArgs = FeaturestoreHelper.setupHudiHiveArgs(hArgs, FeaturestoreHelper.getTableName(name, version));
    
    //Add User-supplied args
    for (Map.Entry<String, String> entry : hudiArgs.entrySet()) {
      hArgs.put(entry.getKey(), entry.getValue());
    }
    
    return hArgs;
  }

  /**
   * Group input parameters into a DTO for creating an on-demand feature group
   *
   * @param jdbcConnectorId id of the jdbc connector to get the on-demand feature group from
   * @return DTO representation of the input parameters
   */
  private FeaturegroupDTO groupInputParamsIntoDTO(Integer jdbcConnectorId) {
    OnDemandFeaturegroupDTO onDemandFeaturegroupDTO = new OnDemandFeaturegroupDTO();
    onDemandFeaturegroupDTO.setFeaturestoreName(featurestore);
    onDemandFeaturegroupDTO.setName(name);
    onDemandFeaturegroupDTO.setVersion(version);
    onDemandFeaturegroupDTO.setDescription(description);
    onDemandFeaturegroupDTO.setQuery(sqlQuery);
    onDemandFeaturegroupDTO.setJdbcConnectorId(jdbcConnectorId);
    return onDemandFeaturegroupDTO;
  }
  
  /**
   * Group input parameters into a DTO for creating a cached feature group
   *
   * @param features feature schema (inferred from the dataframe)
   * @return DTO representation of the input parameters
   */
  private FeaturegroupDTO groupInputParamsIntoDTO(List<FeatureDTO> features){
    if(FeaturestoreHelper.jobNameGetOrDefault(null) != null){
      jobs.add(FeaturestoreHelper.jobNameGetOrDefault(null));
    }
    List<FeaturestoreJobDTO> jobsDTOs = jobs.stream().map(jobName -> {
      FeaturestoreJobDTO featurestoreJobDTO = new FeaturestoreJobDTO();
      featurestoreJobDTO.setJobName(jobName);
      return featurestoreJobDTO;
    }).collect(Collectors.toList());
    CachedFeaturegroupDTO cachedFeaturegroupDTO = new CachedFeaturegroupDTO();
    cachedFeaturegroupDTO.setFeaturestoreName(featurestore);
    cachedFeaturegroupDTO.setName(name);
    cachedFeaturegroupDTO.setVersion(version);
    cachedFeaturegroupDTO.setDescription(description);
    cachedFeaturegroupDTO.setJobs(jobsDTOs);
    cachedFeaturegroupDTO.setFeatures(features);
    cachedFeaturegroupDTO.setOnlineEnabled(online);
    cachedFeaturegroupDTO.setFeatCorrEnabled(featureCorr);
    cachedFeaturegroupDTO.setFeatHistEnabled(featureHistograms);
    cachedFeaturegroupDTO.setDescStatsEnabled(descriptiveStats);
    cachedFeaturegroupDTO.setStatisticColumns(statColumns);
    return cachedFeaturegroupDTO;
  }
  
  public FeaturestoreCreateFeaturegroup setName(String name) {
    this.name = name;
    return this;
  }
  
  public FeaturestoreCreateFeaturegroup setFeaturestore(String featurestore) {
    this.featurestore = featurestore;
    return this;
  }
  
  public FeaturestoreCreateFeaturegroup setSpark(SparkSession spark) {
    this.spark = spark;
    return this;
  }
  
  public FeaturestoreCreateFeaturegroup setVersion(int version) {
    this.version = version;
    return this;
  }
  
  public FeaturestoreCreateFeaturegroup setCorrMethod(String corrMethod) {
    this.corrMethod = corrMethod;
    return this;
  }
  
  public FeaturestoreCreateFeaturegroup setNumBins(int numBins) {
    this.numBins = numBins;
    return this;
  }
  
  public FeaturestoreCreateFeaturegroup setNumClusters(int numClusters) {
    this.numClusters = numClusters;
    return this;
  }

  public FeaturestoreCreateFeaturegroup setMode(String mode) {
    this.mode = mode;
    return this;
  }
  
  public FeaturestoreCreateFeaturegroup setDataframe(Dataset<Row> dataframe) {
    this.dataframe = dataframe;
    return this;
  }
  
  public FeaturestoreCreateFeaturegroup setDescriptiveStats(Boolean descriptiveStats) {
    this.descriptiveStats = descriptiveStats;
    return this;
  }
  
  public FeaturestoreCreateFeaturegroup setFeatureCorr(Boolean featureCorr) {
    this.featureCorr = featureCorr;
    return this;
  }
  
  public FeaturestoreCreateFeaturegroup setFeatureHistograms(Boolean featureHistograms) {
    this.featureHistograms = featureHistograms;
    return this;
  }
  
  public FeaturestoreCreateFeaturegroup setClusterAnalysis(Boolean clusterAnalysis) {
    this.clusterAnalysis = clusterAnalysis;
    return this;
  }
  
  public FeaturestoreCreateFeaturegroup setStatColumns(List<String> statColumns) {
    this.statColumns = statColumns;
    return this;
  }
  
  public FeaturestoreCreateFeaturegroup setJobs(List<String> jobs) {
    this.jobs = jobs;
    return this;
  }
  
  public FeaturestoreCreateFeaturegroup setPrimaryKey(List<String> primaryKey) {
    this.primaryKey = primaryKey;
    return this;
  }
  
  public FeaturestoreCreateFeaturegroup setDescription(String description) {
    this.description = description;
    return this;
  }
  
  public FeaturestoreCreateFeaturegroup setPartitionBy(List<String> partitionBy) {
    this.partitionBy = partitionBy;
    return this;
  }

  public FeaturestoreCreateFeaturegroup setSqlQuery(String sqlQuery) {
    this.sqlQuery = sqlQuery;
    return this;
  }

  public FeaturestoreCreateFeaturegroup setJdbcConnector(String jdbcConnector) {
    this.jdbcConnector = jdbcConnector;
    return this;
  }

  public FeaturestoreCreateFeaturegroup setOnDemand(Boolean onDemand) {
    this.onDemand = onDemand;
    return this;
  }
  
  public FeaturestoreCreateFeaturegroup setHudi(Boolean hudi) {
    this.hudi = hudi;
    return this;
  }
  
  public FeaturestoreCreateFeaturegroup setHudiArgs(Map<String, String> hudiArgs) {
    this.hudiArgs = hudiArgs;
    return this;
  }
  
  public FeaturestoreCreateFeaturegroup setHudiBasePath(String hudiBasePath) {
    this.hudiBasePath = hudiBasePath;
    return this;
  }
  
  public FeaturestoreCreateFeaturegroup setOnline(Boolean online) {
    this.online = online;
    return this;
  }
  
  public FeaturestoreCreateFeaturegroup setOffline(Boolean offline) {
    this.offline = offline;
    return this;
  }
  
  public FeaturestoreCreateFeaturegroup setOnlineTypes(Map<String, String> onlineTypes) {
    this.onlineTypes = onlineTypes;
    return this;
  }
  
}
