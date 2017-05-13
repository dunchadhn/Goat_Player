package org.ggp.base.apps.logging;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;

public class DataLogger {

	private String logfile;

	public DataLogger(String filepath) {
		this.logfile = filepath;
	}

	/*
	 * writes a line of "dataX \t dataY" to the log file
	 */
	public void logDataPoint(Object dataX, Object dataY) {
		FileWriter fileWriter = null;
		try {
			fileWriter = new FileWriter(this.logfile, true);
		} catch (IOException e) {
			System.out.println("ERROR: Log open failed!");
		}
		BufferedWriter output = new BufferedWriter(fileWriter);
		try {
			String dataLine = "" + dataX + '\t' + dataY + '\n';
			output.write(dataLine, 0, dataLine.length());
			output.close();
		} catch (IOException e) {
			System.out.println("ERROR: Log write failed!");
		}
	}

}
