package com.github.leomillon.covidattestationapi

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

@SpringBootApplication
class CovidAttestationApiApplication

fun main(args: Array<String>) {
  runApplication<CovidAttestationApiApplication>(*args)
}
