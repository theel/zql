package zql.spark

import java.util.UUID

import org.apache.spark.sql.SparkSession
import org.slf4j.LoggerFactory
import zql.core.ExecutionPlan._
import zql.core._
import zql.rowbased.Row
import zql.schema.{ JoinedSchema, Schema, SimpleSchema }
import zql.sql.SqlGenerator

import scala.reflect.ClassTag

class SparkSQLTable(val session: SparkSession, val schema: Schema) extends Table {

  override def collectAsList[T: ClassTag](): List[T] = {
    val df = session.sql("select * from " + name)
    df.collect().toList.asInstanceOf[List[T]]
  }

  override def compile(stmt: Statement): Executable[Table] = new SparkSQLStatementCompiler(this, session).compile(stmt, schema)

  override def as(alias: Symbol): Table = new SparkSQLTable(session, schema.as(alias))

  override def collectAsRowList: List[Row] = {
    val list = collectAsList[org.apache.spark.sql.Row]()
    list.map {
      sparkRow => new Row(sparkRow.toSeq.toArray)
    }
  }

  protected def joinWithType(t: Table, jt: JoinType): JoinedTable = t match {
    case st: SparkSQLTable =>
      new JoinedSparkSqlTable(session, this, st, jt)
    case _ =>
      throw new IllegalArgumentException("Cannot join with different table type")
  }
}

object SparkSQLTable {

  def apply(session: SparkSession, tableName: String) = {
    val schema = new SimpleSchema(tableName) {
      session.table(tableName).schema.fields.map(field => addSimpleColDef(Symbol(field.name), field.dataType.getClass)).toSeq
    }
    new SparkSQLTable(session, schema)
  }
}

class JoinedSparkSqlTable(val session: SparkSession, tb1: SparkSQLTable, tb2: SparkSQLTable, joinType: JoinType) extends JoinedTable(tb1, tb2, joinType) {
  override def schema: Schema = new JoinedSchema(tb1, tb2)

  override def name: String = s"joined_${tb1.name}_${tb2.name}"

  override def collectAsList[T: ClassTag](): List[T] = ???

  override def collectAsRowList() = ???

  override def compile(stmt: Statement): Executable[Table] = {
    new SparkSQLStatementCompiler(this, session).compile(stmt, schema)
  }

}

class SparkSQLStatementCompiler(table: Table, session: SparkSession) extends StatementCompiler[SparkSQLTable] {
  val logger = LoggerFactory.getLogger(classOf[SparkSQLStatementCompiler])

  override def compile(stmt: Statement, schema: Schema, option: CompileOption): Executable[SparkSQLTable] = {
    val execPlan = plan("Query") {
      first("Run spark sql") {
        val sqlString = stmt.toSql()
        logger.info("Running sql " + sqlString)
        val df = session.sql(sqlString)

        val newTableName = table.name + "_" + UUID.randomUUID().toString.replace("-", "")
        df.createOrReplaceTempView(newTableName)
        SparkSQLTable(session, newTableName)
      }
    }
    execPlan
  }
}