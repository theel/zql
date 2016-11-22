package zql.list

import zql.core._
import zql.core.util.Utils

import scala.collection.mutable
import scala.reflect.ClassTag

/**
  * Created by tiong on 11/17/16.
  */
class ListTable[ROW: ClassTag](val data: List[ROW], schema: Schema[ROW]) extends Table(schema) {

  class DefaultCompiler(schema: Schema[ROW]) extends Compiler[ListTable[_]]{

    def validate(stmt: Statement) = {
      if (stmt._groupBy!=null){
        val aggFunctions = stmt._selects.filter(_.isInstanceOf[AggregateFunction[_]])
        if (aggFunctions.size==0) {
          throw new IllegalArgumentException("Group by must have at least one aggregation function")
        }
      }
    }

    def compile(stmt: Statement): Executable[ListTable[_]] = {
      import zql.core.ExecutionPlan._
      validate(stmt)

      val selectMappings = compileSelects(stmt._selects, schema)
      val newColumns = selectMappings.map(_._1)
      val resultSchema = new RowSchema(newColumns)
      val execPlan = plan("Query"){
        first("Filter the data"){
          val filteredData = if (stmt._where!=null){
            val filterExtractor = compileColumn[ROW](stmt._where, schema)
            data.filter(d => filterExtractor(d).asInstanceOf[Boolean])
          } else data
          filteredData
        }.next("Grouping the data") {
          filteredData =>
            val selects = selectMappings.map(_._2)
            //TODO: the detection of aggregate func is problematic when we have multi-project before aggregate function
            val groupByIndices = stmt._selects.zipWithIndex.filter(_._1.isInstanceOf[AggregateFunction[_]]).map(_._2).toArray
            val groupedProcessData = if (stmt._groupBy!=null){
              val groupByExtractors = stmt._groupBy.map(compileColumn[ROW](_, schema))
              val groupedData = Utils.groupBy[ROW, Row](filteredData,
                (row: ROW) => groupByExtractors.map(_(row)),
                (row: ROW) => new Row(selects.map(_(row)).toArray),
                (a: Row, b: Row) => a.aggregate(b, groupByIndices)
              ).map(_.normalize).toList

              val havingData = if (stmt._having!=null){
                val havingExtractor = compileColumn[Row](stmt._having, resultSchema)
                groupedData.filter(d => havingExtractor(d).asInstanceOf[Boolean])
              } else {
                groupedData
              }
              havingData
            } else if (groupByIndices.size>0){//this will trigger group by all
              val singleRow = filteredData.map(
                row => new Row(selects.map(_(row)).toArray)
              ).reduce[Row] {
                case (a, b) => a.aggregate(b, groupByIndices)
              }

              List(singleRow.normalize)
            }
            else {
              filteredData.map{
                d =>
                  new Row(selects.map(_(d)).toArray)
              }
            }
            groupedProcessData
        }.next("Return the table") {
          groupedProcessData =>
            val names = stmt._selects.map(_.getName)
            new ListTable(groupedProcessData.toList, resultSchema)
        }
      }
      execPlan
    }

    def compileSelects[ROW](selects: Seq[Column], schema: Schema[ROW]): Seq[(Symbol, ColumnAccessor[ROW, _])] = {
      selects.flatMap {
          case ac: AllColumn =>
            schema.columnAccessors().map {
              case (name, accessor) => (name, accessor.asInstanceOf[ColumnAccessor[ROW, _]])
            }
          case c: Column =>
            Seq((c.getName, compileColumn[ROW](c, schema)))
      }
    }

    def compileColumn[ROW](col: Column, schema: Schema[ROW]): ColumnAccessor[ROW, _] = {
      col match {
        case cc: WithAccessor[_] =>
          cc.getColumnAccessor[ROW](this, schema)
        case c: NamedColumn[_] =>
          if (schema.columnAccessors().contains(c.name)) {
            schema.columnAccessors()(c.name)
              .asInstanceOf[ColumnAccessor[ROW, _]]
          } else throw new IllegalArgumentException(s"Invalid column '${c.name}'")
        case _ =>
          throw new IllegalArgumentException("Unknown column type " + col)
      }
    }
  }

  def compile(stmt: Statement) = {
    new DefaultCompiler(schema).compile(stmt)
  }

  override def collectAsList = data
}
