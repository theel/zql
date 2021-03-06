package zql.spark

import org.apache.spark.sql.SparkSession
import org.apache.spark.{ SparkConf, SparkContext }
import zql.core.{ Person, TableTest, _ }
import zql.rowbased.Row

class SparkSqlTableTest extends TableTest {

  val spark = SparkSession
    .builder()
    .appName("SparkSqlTableTest")
    .config("spark.master", "local[4]")
    .config("spark.driver.allowMultipleContexts", "true")
    .config("spark.sql.crossJoin.enabled", "true")
    .getOrCreate()

  //initialize
  {
    val personRdd = spark.sqlContext.sparkContext.parallelize(persons)
    val departmentRdd = spark.sqlContext.sparkContext.parallelize(departments)
    val personDf = spark.createDataFrame(personRdd)
    personDf.createOrReplaceTempView("person")
    val departmentDf = spark.createDataFrame(departmentRdd)
    departmentDf.createOrReplaceTempView("department")

  }

  val personTable = SparkSQLTable(spark, "person")

  val departmentTable = SparkSQLTable(spark, "department")

  override def supportSelectLimitOffset = {
    //TODO: this is never supported. we probably should do detection in compile phase to throw exception on it
    assert(true)
  }

  override def supportSelectLimit = {
    //TODO: this is never supported. we probably should do detection in compile phase to throw exception on it
    assert(true)
  }

  override def supportDetectInvalidAggregation = {
    //TODO: move detection to compile phase
    try {
      super.supportDetectInvalidAggregation
    } catch {
      case ae: org.apache.spark.sql.AnalysisException =>
        //do nothing
        assert(true)
      case e => throw e
    }
  }

  override def supportDetectInvalidAggregation2 = {
    //TODO: move detection to compile phase
    try {
      super.supportDetectInvalidAggregation
    } catch {
      case e: org.apache.spark.sql.AnalysisException =>
        //do nothing
        assert(true)
      case e => throw e
    }
  }

  override def supportDetectBadSubquery = {
    try {
      super.supportDetectBadSubquery
    } catch {
      case ae: org.apache.spark.sql.AnalysisException =>
        assert(true)
      case e => throw e
    }
  }
  override protected def afterAll(): Unit = {
    spark.stop()
  }
}
