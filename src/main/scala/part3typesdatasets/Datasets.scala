package part3typesdatasets

import java.sql.Date

import org.apache.spark.sql.{DataFrame, Dataset, Encoders, SparkSession}
import org.apache.spark.sql.functions._


object Datasets extends App {

  val spark = SparkSession.builder()
    .appName("Datasets")
    .config("spark.master", "local")
    .getOrCreate()

  val numbersDF: DataFrame = spark.read
    .format("csv")
    .option("header", "true")
    .option("inferSchema", "true")
    .load("src/main/resources/data/numbers.csv")

  numbersDF.printSchema()

  // convert a DF to a Dataset
  implicit val intEncoder = Encoders.scalaInt
  val numbersDS: Dataset[Int] = numbersDF.as[Int]

  // dataset of a complex type
  // 1 - define your case class
  case class Car(
                  Name: String,
                  Miles_per_Gallon: Option[Double],
                  Cylinders: Long,
                  Displacement: Double,
                  Horsepower: Option[Long],
                  Weight_in_lbs: Long,
                  Acceleration: Double,
                  Year: String,
                  Origin: String
                )

  // 2 - read the DF from the file
  def readDF(filename: String) = spark.read
    .option("inferSchema", "true")
    .json(s"src/main/resources/data/$filename")

  val carsDF = readDF("cars.json")

  // 3 - define an encoder (importing the implicits)
  import spark.implicits._
  // 4 - convert the DF to DS
  // If we don't import spark implicit, we have to import Product Encoder, means Ecoding class that extends Products
  // and case class already extends from Product but we may use spark.implicits._
  // implicit val carEncoder = Encoders.product[Car]
  val carsDS = carsDF.as[Car]

  // DS collection functions
  numbersDS.filter(_ < 100)

  // map, flatMap, fold, reduce, for comprehensions ...
  val carNamesDS = carsDS.map(car => car.Name.toUpperCase())
  // Dataframe is not dealing with the types that is why car below(won't compile) is Row and above Datasets.Car
  // val carNamesDF = carsDF.map(car => car.Name.toUpperCase())

  // NOTE: Datasets are cost effective, bcz spark has to apply on each row type and filter operation to have
  // the scala object and than perform operation.
  // RESULT: If type is important us Dataset but for better performance still use Dataframe
  /**
    * Exercises
    *
    * 1. Count how many cars we have
    * 2. Count how many POWERFUL cars we have (HP > 140)
    * 3. Average HP for the entire dataset
    */

  // 1
  val carsCount = carsDS.count
  println(carsCount)

  // 2
  println(carsDS.filter(_.Horsepower.getOrElse(0L) > 140).count)

  // 3
  println(carsDS.map(_.Horsepower.getOrElse(0L)).reduce(_ + _) / carsCount)

  // also use the DF functions!
  carsDS.select(avg(col("Horsepower")))


  // Joins
  // NOTE: Initially id is kept as Int, but when we say "inferschema" "true", spark would consider all numbers
  // as Long that is bigint.
  case class Guitar(id: Long, make: String, model: String, guitarType: String)
  case class GuitarPlayer(id: Long, name: String, guitars: Seq[Long], band: Long)
  case class Band(id: Long, name: String, hometown: String, year: Long)

  val guitarsDS = readDF("guitars.json").as[Guitar]
  val guitarPlayersDS = readDF("guitarPlayers.json").as[GuitarPlayer]
  val bandsDS = readDF("bands.json").as[Band]

  // If we use "join" method we would lose type information and we would get dataframe instead of dataset
  // to keep the type information, we should use "joinwith"
  val guitarPlayerBandsDS: Dataset[(GuitarPlayer, Band)] = guitarPlayersDS.joinWith(bandsDS,
    guitarPlayersDS.col("band") === bandsDS.col("id"),
    "inner")

  /**
    * Exercise: join the guitarsDS and guitarPlayersDS, in an outer join
    * (hint: use array_contains)
    */

  guitarPlayersDS
    .joinWith(guitarsDS, array_contains(guitarPlayersDS.col("guitars"), guitarsDS.col("id")), "outer")
    .show(false)

  // Grouping DS

  val carsGroupedByOrigin = carsDS
    .groupByKey(_.Origin)
    .count()
    .show()

  // joins and groups are WIDE transformations, will involve SHUFFLE operations

}
