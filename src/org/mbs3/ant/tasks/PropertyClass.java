/*
 *   Copyright 2007 Martin B. Smith
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 * 
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */
package org.mbs3.ant.tasks;

import org.apache.tools.ant.Task;
import org.apache.tools.ant.Project;
import org.apache.tools.ant.BuildException;
import org.apache.tools.ant.types.FileSet;
import org.apache.tools.ant.DirectoryScanner;

import java.util.ArrayList;
import java.util.Properties;
import java.util.Enumeration;

import java.io.File;
import java.io.BufferedInputStream;
import java.io.FileInputStream;
import java.io.FileWriter;
import java.io.IOException;

/**
 *  An Ant task for making a static class of symbols from keys of a property file
 * 
 *  <p>This class takes a root destination, package name, class name, a property file, and an
 *  optional parameter to capitalize keys from the property file and produces a class file 
 *  full of static variables that define the keys of the class file as the variable names,
 *  and string versions of the variables as the values.</p>
 * 
 *  <p>The purpose of this class is to let programmers use the compiler for syntax checking of
 *  localization property keys while still giving the flexibility to load new values in for the 
 *  properties. Alternatives like using a static class file for defaults make it harder to 
 *  dynamically reload the property values, and alternatives like a strict property file don't
 *  provide the advantage of syntax checking to ensure all property file keys referenced exist.</p>
 *  
 *  <p>Use the following to enable this task in Ant:<br>
 *  	<code>&lt;taskdef
 *		name="propertyClass" 
 *		classname="org.mbs3.ant.tasks.PropertyClass"
 *		classpath="Archive.jar"
 *		/&gt;</code>
 *	</p>
 *
 * <p>Use the following syntax to use this task in Ant:<br> 
 * 		<code>&lt;target name="test"&gt;
 *			&lt;propertyClass 
 *				inputfile="sample.properties"
 *				destdir="src" 
 *				classname="SampleClass"
 *				packageName="org.mbs3.ant.test.destination.package"
 *			/&gt;
 * 		&lt;/target&gt;</code>
 *	</p>
 *
 * @author Martin Smith
 */

public class PropertyClass extends Task {

	File destDir, inputFile;
	ArrayList<FileSet> fileSets;
	String className,packageName;
	boolean convertToUpper = false;
	
	public PropertyClass()
	{
		this.fileSets = new ArrayList<FileSet>();
	}

	public void execute() {
		// add the package name and figure out the real destination, if no package this will still succeed 
		File realDestDir = new File(destDir.getAbsolutePath() + File.separator + packageName.replace(".", File.separator));
		
		// create path for file if the folders don't exist
		try {
			if(realDestDir.mkdirs())
				log("Created directory " + realDestDir.getAbsolutePath());
		} catch (Exception ex) {
			throw new BuildException(ex);
		}
		
		// create the output file from the real dest dir and the class name and .java
		File realOutputFile = new File(realDestDir.getAbsolutePath() + File.separator + this.className +".java");

		// decide if we need to update the file -- compare timestamps on properties file and generated source
		if(realOutputFile.canRead() && inputFile.lastModified() <= realOutputFile.lastModified())
		{
			// properties file was modified before or on the time that the source file was made
			// so we don't need to act any further -- the source file should be good for now
			return;
		}
		
		// attempt to load the properties file we're going to use for input
		Properties p = new Properties();
		try {
			if((inputFile == null || inputFile.canRead()) && (fileSets == null || fileSets.isEmpty()))
				throw new BuildException("You need to supply some readable property files to this task");
			
			if(inputFile != null && inputFile.canRead())
				p.load(new BufferedInputStream(new FileInputStream(inputFile)));
			
			if(fileSets != null && !fileSets.isEmpty())
			{
				log(fileSets.size() + " file sets included",Project.MSG_INFO);
				for(FileSet fs : fileSets)
				{
					
					DirectoryScanner ds = fs.getDirectoryScanner(); ds.scan();
					log("Included " + ds.getIncludedFilesCount() + " files", Project.MSG_INFO);
					
					String fileNames [] = ds.getIncludedFiles();
					for(String fileName : fileNames)
					{
						log("Stuff: " + fileName, Project.MSG_INFO);
						p.load(new BufferedInputStream(new FileInputStream(new File(fileName))));
					}
				}
			}
		} catch (Exception ex) {
			throw new BuildException(ex);
		}

		// check if we can write to this file we're going to use
		try {
			if(realOutputFile.createNewFile())
				log("Couldn't find output file, creating a new one", Project.MSG_INFO);

			if(!realOutputFile.canWrite())
				throw new BuildException("Cannot write to " + realOutputFile.getAbsolutePath());
		} catch (IOException ex) {
			throw new BuildException(ex);
		}
		
		// begin creating output, starting with comment to not edit the file
		StringBuilder output = new StringBuilder();
		output.append("/**\n * DO NOT EDIT! This file has been generated by Ant\n **/\n\n");
		
		// if we're given a package, include
		if(this.packageName != null)
			output.append("package " + packageName + ";\n\n");
		
		// if we aren't given a class name, halt and complain
		if(this.className != null)
			output.append("public class " + this.className + " {\n");
		else
			throw new BuildException("Cannot write class without a class name");

		// collect the property keys and create output that defines constants from the keys
		Enumeration keys = p.keys();
		while(keys.hasMoreElements())
		{
			String key = ((String)keys.nextElement());
			
			if(convertToUpper)
				key = key.toUpperCase();
			
			output.append("\tpublic static final String " + key + " = \"" + key + "\";\n");
		}
		output.append("}\n");
		
		// write the file now that we're sure it is safe
		try {
			FileWriter fw = new FileWriter(realOutputFile);
			fw.write(output.toString());
			fw.close();
		} catch (IOException ex) {
			throw new BuildException(ex);
		}
		log("Wrote file " + realOutputFile.getAbsolutePath(), Project.MSG_INFO);
	}

	public void setInputfile(File inputFile) {
		this.inputFile = inputFile;
	}
	
	public void setDestdir(File destDir)
	{
		this.destDir = destDir;
	}
	
	public void setClassname(String className)
	{
		this.className = className;
	}
	
	public void setPackagename(String packageName)
	{
		this.packageName = packageName;
	}

	public void setConverttoupper(boolean b) {
		this.convertToUpper = b;
	}
	
	public void addFileSet(FileSet fileSet)
	{
		this.fileSets.add(fileSet);
	}

}