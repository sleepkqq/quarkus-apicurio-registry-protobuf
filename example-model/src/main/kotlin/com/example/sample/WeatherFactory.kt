package com.example.sample

import com.example.weather.Reading
import com.example.weather.reading

object WeatherFactory {

	fun sample(): Reading = reading {
		station = "KSFO"
		temperature = 18.5
		condition = "Foggy"
	}
}
