package cn.edu.seu.cat.demo.pagerank.tool

import java.io.{BufferedWriter, File, FileWriter}

import org.apache.commons.math3.random.RandomDataImpl

/**
 * Created by snow on 15/6/10.
 */
object PageRankGraphGenerator extends App {

  val pw0 = new BufferedWriter(new FileWriter(new File("demo_data/PageRank/BigPageRankData")))

  val numberOfVertex = 100000L


  val random = new RandomDataImpl
  val averageDegree = 5

  for (vertexId <- 1L to numberOfVertex) {
    var vertices = ""
    for (tick <- 1 to averageDegree + (random.nextInt(0, 4) - 2)) {
      vertices += (random.nextLong(1L, numberOfVertex).toString + "-")
    }
    vertices = vertices.substring(0, vertices.length - 2)
    pw0.write(s"${vertexId} ${vertices}\n")
  }

  pw0.close
}
