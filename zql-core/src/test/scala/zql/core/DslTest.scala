package zql.core

import org.scalatest._
import zql.list.ListTable

class DslTest extends FlatSpec with Matchers with PersonExample {

  val table = ListTable[Person]('id, 'firstName, 'lastName, 'age)(data)

  "Here are all the supported syntax for the dsl" should "just compile" in {

    //all the possible selects
    select(*) from table
    select('firstName, 'lastName, "Test") from table //simple select
    select('firstName, sum('age)) from table //select with UDF
    select('firstName) from table where ('firstName === 'lastName) //select with condition
    select('firstName) from table groupBy ('firstName, 'lastName) //select with group by
    select('firstName) from table orderBy ('firstName) //select with order by
    select('firstName) from table limit (1, 10)
    selectDistinct('firstName) from table
    select(countDistinct('firstName)) from table

    //all the where
    val wherePart = select('firstName) from table where ('firstName === 'lastName) //select with condition
    wherePart groupBy ('firstName)
    wherePart orderBy ('firstName)
    wherePart limit (1, 10)

    //all the groupby
    val groupPart = wherePart groupBy ('firstName)
    groupPart orderBy ('firstName)
    groupPart having ('firstName === 'lastName)
    groupPart limit (1, 10)

    //orderby
    val orderByPart = select('firstName) from table orderBy ('firstName) //select with order by
    orderByPart limit (1, 10)

    //all supported condition
    ('firstName === 'lastName) and ('firstName !== 'lastName)
    ('firstName === 'lastName) or NOT('firstName !== 'lastName)

    //functions
    NOT('age)
    sum('age)
    count('age)
    countDistinct('age)

    //subquery
    select(select(1)) from table
    select(*) from (select(*) from table)

  }
}