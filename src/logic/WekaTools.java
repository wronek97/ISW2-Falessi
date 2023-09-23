package logic;

import weka.core.Instance;
import weka.core.Instances;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.NumberFormat;
import java.util.List;
import java.util.Locale;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import logic.FileMetrics.CSV_Mode;
import weka.attributeSelection.BestFirst;
import weka.attributeSelection.CfsSubsetEval;
import weka.classifiers.CostMatrix;
import weka.classifiers.Evaluation;
import weka.classifiers.bayes.NaiveBayes;
import weka.classifiers.lazy.IBk;
import weka.classifiers.meta.CostSensitiveClassifier;
import weka.classifiers.trees.J48;
import weka.classifiers.trees.RandomForest;
import weka.core.converters.ArffSaver;
import weka.core.converters.CSVLoader;
import weka.core.converters.ConverterUtils.DataSource;
import weka.filters.Filter;
import weka.filters.supervised.attribute.AttributeSelection;
import weka.filters.supervised.instance.Resample;
import weka.filters.supervised.instance.SMOTE;

public class WekaTools{
	public static final String CSV = ".csv";
	public static final String ARFF = ".arff";
	
	private WekaTools() {
	    throw new IllegalStateException("WekaTools is a static class");
	}
	
	public static void convertCsvToArff(String csvSource, String arffOutput) {
		CSVLoader loader = new CSVLoader();
		ArffSaver saver = new ArffSaver();
		
		try {
			// load CSV
			loader.setSource(new File(csvSource));
			Instances data = loader.getDataSet();
	
			// save ARFF
			if(!data.isEmpty()) {
				saver.setInstances(data);
				saver.setFile(new File(arffOutput));
				saver.writeBatch();
			}
			else {
				try (FileWriter fileWriter = new FileWriter(arffOutput)) {
					StringBuilder outputBuilder = new StringBuilder("");
					outputBuilder.append("@relation Training1\n\n");
					outputBuilder.append("@attribute Version numeric\n");
					outputBuilder.append("@attribute Size numeric\n");
					outputBuilder.append("@attribute LOC_touched numeric\n");
					outputBuilder.append("@attribute LOC_added numeric\n");
					outputBuilder.append("@attribute MAX_LOC_added numeric\n");
					outputBuilder.append("@attribute AVG_LOC_added numeric\n");
					outputBuilder.append("@attribute Churn numeric\n");
					outputBuilder.append("@attribute MAX_Churn numeric\n");
					outputBuilder.append("@attribute AVG_Churn numeric\n");
					outputBuilder.append("@attribute NR numeric\n");
					outputBuilder.append("@attribute NF numeric\n");
					outputBuilder.append("@attribute Bugged {false,true}\n\n");
					outputBuilder.append("@data\n");
					
					fileWriter.append(outputBuilder.toString());
				}
			}
			
			
		} catch (IOException e) {
			Logger logger = Logger.getLogger(WekaTools.class.getName());
			logger.log(Level.SEVERE, "Error converting csv to arff", e);
		}
		
	}
	
	public static void convertAllCsvToArff(String sourcesPath) {
		List<String> files = null;
		try (Stream<Path> walk = Files.walk(Paths.get(sourcesPath))) {
			files = walk.map(Path::toString).filter(f -> f.endsWith(CSV)).collect(Collectors.toList());
		} catch (IOException e) {
			Logger logger = Logger.getLogger(WekaTools.class.getName());
			logger.log(Level.SEVERE, "Error converting csv to arff", e);
	    }
		
		for(String file : files) {
			WekaTools.convertCsvToArff(file, file.replace(CSV, ARFF));
		}
	}
	
	public static void generateArff(String sourcesPath, String csvSource, String arffOutput) {
		CSVLoader loader = new CSVLoader();
		String fileName = csvSource.replace(sourcesPath, "").replace("\\", "").replace(CSV, "");
		
		try (FileWriter fileWriter = new FileWriter(arffOutput)) {
			StringBuilder outputBuilder = new StringBuilder("");
			outputBuilder.append("@relation " + fileName + "\n\n");
			outputBuilder.append("@attribute Version numeric\n");
			outputBuilder.append("@attribute Size numeric\n");
			outputBuilder.append("@attribute LOC_touched numeric\n");
			outputBuilder.append("@attribute LOC_added numeric\n");
			outputBuilder.append("@attribute MAX_LOC_added numeric\n");
			outputBuilder.append("@attribute AVG_LOC_added numeric\n");
			outputBuilder.append("@attribute Churn numeric\n");
			outputBuilder.append("@attribute MAX_Churn numeric\n");
			outputBuilder.append("@attribute AVG_Churn numeric\n");
			outputBuilder.append("@attribute NR numeric\n");
			outputBuilder.append("@attribute NF numeric\n");
			outputBuilder.append("@attribute Bugged {false,true}\n\n");
			outputBuilder.append("@data\n");
			
			fileWriter.append(outputBuilder.toString());
			
			// load CSV
			loader.setSource(new File(csvSource));
			Instances data = loader.getDataSet();
	
			// save ARFF
			if(!data.isEmpty()) {
				for(Instance ins : data) {
					fileWriter.append(ins.toString() + "\n");
				}
			}
			
		} catch (IOException e) {
			Logger logger = Logger.getLogger(WekaTools.class.getName());
			logger.log(Level.SEVERE, "Error generating arffs", e);
		}
		
	}
	
	public static void generateAllArff(String sourcesPath) {
		List<String> files = null;
		try (Stream<Path> walk = Files.walk(Paths.get(sourcesPath))) {
			files = walk.map(Path::toString).filter(f -> f.endsWith(CSV)).collect(Collectors.toList());
		} catch (IOException e) {
			Logger logger = Logger.getLogger(WekaTools.class.getName());
			logger.log(Level.SEVERE, "Error generating arffs", e);
	    }
		
		for(String file : files) {
			WekaTools.generateArff(sourcesPath, file, file.replace(CSV, ARFF));
		}
	}
	
	private static double divideIfPossible(Integer a, Integer b) {
		if(b != 0) {
			return (double) a / b;
		}
		
		return 0f;
	}
	
	public static void walkForwardToCSV(String sourcesPath, Integer versionsToAnalyze, String savePath, String projectName, CSV_Mode mode) {
		String outname = savePath + "\\" + projectName + "WekaAnalysis.csv";
		String[] classifiersList = {"NaiveBayes", "IBk", "RandomForest", "J48"};
		Evaluation[] evaluationsList = new Evaluation[classifiersList.length];
		Evaluation[] filteredEvals = new Evaluation[classifiersList.length];
		Evaluation[] underSamplingEvals = new Evaluation[classifiersList.length];
		Evaluation[] overSamplingEvals = new Evaluation[classifiersList.length];
		Evaluation[] smoteSamplingEvals = new Evaluation[classifiersList.length];
		Evaluation[] sensitiveThresholdEvals = new Evaluation[classifiersList.length];
		Evaluation[] sensitiveLearningEvals = new Evaluation[classifiersList.length];
		StringBuilder outputBuilder;
		
		DataSource trainingSource;
		DataSource testSource;
		Instances training;
		Instances test;
		
		if(mode == CSV_Mode.IT) {
			outputBuilder = new StringBuilder("Test Release;# Training Releases;Training %;Training Defective %;Test Defective %;Classifier;Balancing;Feature Selection;Sensitivity;TP;FP;TN;FN;TP Rate;FP Rate;Precision;Recall;F-Measure;AUC;Kappa;Accuracy\n");
		}
		else {
			outputBuilder = new StringBuilder("Test Release,# Training Releases,Training %,Training Defective %,Test Defective %,Classifier,Balancing,Feature Selection,Sensitivity,TP,FP,TN,FN,TP Rate,FP Rate,Precision,Recall,F-Measure,AUC,Kappa,Accuracy\n");
		}
		
		try (FileWriter fileWriter = new FileWriter(outname)) {
			//start from release 2, which has at least one training release
			for(int k = 1; k < versionsToAnalyze; k++) {
				trainingSource = new DataSource(sourcesPath + "\\" + "Training" + (k+1) + ARFF);
				testSource = new DataSource(sourcesPath + "\\" + "Test" + (k+1) + ARFF);
				training = trainingSource.getDataSet();
				test = testSource.getDataSet();
	
				int numAttr = training.numAttributes();
				training.setClassIndex(numAttr - 1); // l'indice è relativo all'ultima colonna (Bugged)
				test.setClassIndex(numAttr - 1);
				
				Integer trainingNotBugged = WekaTools.getInstancesNumberForAttribute(training, numAttr - 1, 0);
				Integer trainingBugged = WekaTools.getInstancesNumberForAttribute(training, numAttr - 1, 1);
				Integer testBugged = WekaTools.getInstancesNumberForAttribute(test, numAttr - 1, 1);
				Integer majority;
				Integer minority;
				if(trainingBugged < trainingNotBugged) {
					majority = trainingNotBugged;
					minority = trainingBugged;
				}
				else {
					majority = trainingBugged;
					minority = trainingNotBugged;
				}
				
				double trainingPercentage = WekaTools.divideIfPossible(training.size(), training.size() + test.size());
				double trainingDefectivePerc = WekaTools.divideIfPossible(trainingBugged, trainingBugged + trainingNotBugged);
				double testDefectivePerc = WekaTools.divideIfPossible(testBugged, (trainingBugged + testBugged));
				
				//STANDARD CLASSIFIERS
				NaiveBayes nbClassifier = new NaiveBayes();
				evaluationsList[0] = new Evaluation(test);
				nbClassifier.buildClassifier(training);
				evaluationsList[0].evaluateModel(nbClassifier, test);
				
				IBk ibkClassifier = new IBk(11);
				evaluationsList[1] = new Evaluation(test);
				ibkClassifier.buildClassifier(training);
				evaluationsList[1].evaluateModel(ibkClassifier, test);
	
				RandomForest randomForestClassifier = new RandomForest();
				evaluationsList[2] = new Evaluation(test);
				randomForestClassifier.buildClassifier(training);
				evaluationsList[2].evaluateModel(randomForestClassifier, test);
				
				J48 j48Classifier = new J48();
				evaluationsList[3] = new Evaluation(test);
				j48Classifier.buildClassifier(training);
				evaluationsList[3].evaluateModel(j48Classifier, test);
				
				
				//SELECTION
				AttributeSelection attributeSelection = new AttributeSelection();
				CfsSubsetEval cfsSubsetEval = new CfsSubsetEval();
				BestFirst bestFirst = new BestFirst(); //standard BestFirst with no other sets
				attributeSelection.setEvaluator(cfsSubsetEval);
				attributeSelection.setSearch(bestFirst);
				attributeSelection.setInputFormat(training);
				training = Filter.useFilter(training, attributeSelection);
				test = Filter.useFilter(test, attributeSelection);
				
				NaiveBayes selectionNbClassifier = new NaiveBayes();
				filteredEvals[0] = new Evaluation(test);
				selectionNbClassifier.buildClassifier(training);
				filteredEvals[0].evaluateModel(selectionNbClassifier, test);
				
				IBk selectionIbkClassifier = new IBk(11);
				filteredEvals[1] = new Evaluation(test);
				selectionIbkClassifier.buildClassifier(training);
				filteredEvals[1].evaluateModel(selectionIbkClassifier, test);
	
				RandomForest selectionRandomForestClassifier = new RandomForest();
				filteredEvals[2] = new Evaluation(test);
				selectionRandomForestClassifier.buildClassifier(training);
				filteredEvals[2].evaluateModel(selectionRandomForestClassifier, test);
				
				J48 selectionJ48Classifier = new J48();
				filteredEvals[3] = new Evaluation(test);
				selectionJ48Classifier.buildClassifier(training);
				filteredEvals[3].evaluateModel(selectionJ48Classifier, test);
				
				//SAMPLING
				Resample resample;
				test = testSource.getDataSet(); //reset DataSet to the original
				test.setClassIndex(numAttr - 1);
				
				//UnderSampling
				training = trainingSource.getDataSet(); //reset DataSet to the original
				training.setClassIndex(numAttr - 1); //l'indice è relativo all'ultima colonna (Bugged)
				resample = getUnderSampling(majority, minority);
				resample.setInputFormat(training);
				training = Filter.useFilter(training, resample);
				
				NaiveBayes nbUnderSampling = new NaiveBayes();
				underSamplingEvals[0] = new Evaluation(test);
				nbUnderSampling.buildClassifier(training);
				underSamplingEvals[0].evaluateModel(nbUnderSampling, test);
				
				IBk ibkUnderSampling = new IBk(11);
				underSamplingEvals[1] = new Evaluation(test);
				ibkUnderSampling.buildClassifier(training);
				underSamplingEvals[1].evaluateModel(ibkUnderSampling, test);
				
				RandomForest randomForestUnderSampling = new RandomForest();
				underSamplingEvals[2] = new Evaluation(test);
				randomForestUnderSampling.buildClassifier(training);
				underSamplingEvals[2].evaluateModel(randomForestUnderSampling, test);
				
				J48 j48UnderSampling = new J48();
				underSamplingEvals[3] = new Evaluation(test);
				j48UnderSampling.buildClassifier(training);
				underSamplingEvals[3].evaluateModel(j48UnderSampling, test);
				
				//OverSampling
				training = trainingSource.getDataSet(); //reset DataSet to the original
				training.setClassIndex(numAttr - 1); // l'indice è relativo all'ultima colonna (Bugged)
				resample = getOverSampling(majority, minority);
				resample.setInputFormat(training);
				training = Filter.useFilter(training, resample);
				
				NaiveBayes nbOverSampling = new NaiveBayes();
				overSamplingEvals[0] = new Evaluation(test);
				nbOverSampling.buildClassifier(training);
				overSamplingEvals[0].evaluateModel(nbOverSampling, test);
				
				IBk ibkOverSampling = new IBk(11);
				overSamplingEvals[1] = new Evaluation(test);
				ibkOverSampling.buildClassifier(training);
				overSamplingEvals[1].evaluateModel(ibkOverSampling, test);
				
				RandomForest randomForestOverSampling = new RandomForest();
				overSamplingEvals[2] = new Evaluation(test);
				randomForestOverSampling.buildClassifier(training);
				overSamplingEvals[2].evaluateModel(randomForestOverSampling, test);
				
				J48 j48OverSampling = new J48();
				overSamplingEvals[3] = new Evaluation(test);
				j48OverSampling.buildClassifier(training);
				overSamplingEvals[3].evaluateModel(j48OverSampling, test);
				
				
				//SMOTE
				training = trainingSource.getDataSet(); //reset DataSet to the original
				training.setClassIndex(numAttr - 1); // l'indice è relativo all'ultima colonna (Bugged)
				SMOTE smote = getSMOTESampling(majority, minority);
				smote.setInputFormat(training);
				training = Filter.useFilter(training, smote);
				
				NaiveBayes nbSMOTE = new NaiveBayes();
				smoteSamplingEvals[0] = new Evaluation(test);
				nbSMOTE.buildClassifier(training);
				smoteSamplingEvals[0].evaluateModel(nbSMOTE, test);
				
				IBk ibkSMOTE = new IBk(11);
				smoteSamplingEvals[1] = new Evaluation(test);
				ibkSMOTE.buildClassifier(training);
				smoteSamplingEvals[1].evaluateModel(ibkSMOTE, test);
				
				RandomForest randomForestSMOTE = new RandomForest();
				smoteSamplingEvals[2] = new Evaluation(test);
				randomForestSMOTE.buildClassifier(training);
				smoteSamplingEvals[2].evaluateModel(randomForestSMOTE, test);
				
				J48 j48SMOTE = new J48();
				smoteSamplingEvals[3] = new Evaluation(test);
				j48SMOTE.buildClassifier(training);
				smoteSamplingEvals[3].evaluateModel(j48SMOTE, test);
				
				
				//SENSITIVE
				training = trainingSource.getDataSet(); //reset DataSet to the original
				training.setClassIndex(numAttr - 1);
				CostSensitiveClassifier costSensitiveClassifier = new CostSensitiveClassifier();
				costSensitiveClassifier.setCostMatrix(createCostMatrix(10.0, 1.0));
				
				//Sensitive Threshold
				costSensitiveClassifier.setMinimizeExpectedCost(true);
				
				costSensitiveClassifier.setClassifier(nbClassifier);
				sensitiveThresholdEvals[0] = new Evaluation(test, costSensitiveClassifier.getCostMatrix());
				costSensitiveClassifier.buildClassifier(training);
				sensitiveThresholdEvals[0].evaluateModel(costSensitiveClassifier, test);
				
				costSensitiveClassifier.setClassifier(ibkClassifier);
				sensitiveThresholdEvals[1] = new Evaluation(test, costSensitiveClassifier.getCostMatrix());
				costSensitiveClassifier.buildClassifier(training);
				sensitiveThresholdEvals[1].evaluateModel(costSensitiveClassifier, test);
				
				costSensitiveClassifier.setClassifier(randomForestClassifier);
				sensitiveThresholdEvals[2] = new Evaluation(test, costSensitiveClassifier.getCostMatrix());
				costSensitiveClassifier.buildClassifier(training);
				sensitiveThresholdEvals[2].evaluateModel(costSensitiveClassifier, test);
				
				costSensitiveClassifier.setClassifier(j48Classifier);
				sensitiveThresholdEvals[3] = new Evaluation(test, costSensitiveClassifier.getCostMatrix());
				costSensitiveClassifier.buildClassifier(training);
				sensitiveThresholdEvals[3].evaluateModel(costSensitiveClassifier, test);
				
				//Sensitive Learning
				costSensitiveClassifier.setMinimizeExpectedCost(false);
				
				costSensitiveClassifier.setClassifier(nbClassifier);
				sensitiveLearningEvals[0] = new Evaluation(test, costSensitiveClassifier.getCostMatrix());
				costSensitiveClassifier.buildClassifier(training);
				sensitiveLearningEvals[0].evaluateModel(costSensitiveClassifier, test);
				
				costSensitiveClassifier.setClassifier(ibkClassifier);
				sensitiveLearningEvals[1] = new Evaluation(test, costSensitiveClassifier.getCostMatrix());
				costSensitiveClassifier.buildClassifier(training);
				sensitiveLearningEvals[1].evaluateModel(costSensitiveClassifier, test);
				
				costSensitiveClassifier.setClassifier(randomForestClassifier);
				sensitiveLearningEvals[2] = new Evaluation(test, costSensitiveClassifier.getCostMatrix());
				costSensitiveClassifier.buildClassifier(training);
				sensitiveLearningEvals[2].evaluateModel(costSensitiveClassifier, test);
				
				costSensitiveClassifier.setClassifier(j48Classifier);
				sensitiveLearningEvals[3] = new Evaluation(test, costSensitiveClassifier.getCostMatrix());
				costSensitiveClassifier.buildClassifier(training);
				sensitiveLearningEvals[3].evaluateModel(costSensitiveClassifier, test);
				
				
				NumberFormat numberFormat;
				String separator;
				if(mode == CSV_Mode.IT) {
					numberFormat = NumberFormat.getInstance(Locale.ITALY);
				    numberFormat.setGroupingUsed(false);
				    separator = ";";
				}
				else {
					numberFormat = NumberFormat.getInstance(Locale.US);
				    numberFormat.setGroupingUsed(false);
				    separator = ",";
				}
				
				for (int i = 0; i < classifiersList.length; i++) {
					outputBuilder.append((k+1) + separator);
					outputBuilder.append(k + separator);
					outputBuilder.append(numberFormat.format(trainingPercentage) + separator);
					outputBuilder.append(numberFormat.format(trainingDefectivePerc) + separator);
					outputBuilder.append(numberFormat.format(testDefectivePerc) + separator);
					outputBuilder.append(classifiersList[i] + separator);
					outputBuilder.append("None" + separator);
					outputBuilder.append("None" + separator);
					outputBuilder.append("None" + separator);
					outputBuilder.append(numberFormat.format(evaluationsList[i].numTruePositives(1)) + separator);
					outputBuilder.append(numberFormat.format(evaluationsList[i].numFalsePositives(1)) + separator);
					outputBuilder.append(numberFormat.format(evaluationsList[i].numTrueNegatives(1)) + separator);
					outputBuilder.append(numberFormat.format(evaluationsList[i].numFalseNegatives(1)) + separator);
					outputBuilder.append(numberFormat.format(evaluationsList[i].truePositiveRate(1)) + separator);
					outputBuilder.append(numberFormat.format(evaluationsList[i].falsePositiveRate(1)) + separator);
					outputBuilder.append(numberFormat.format(evaluationsList[i].precision(1)) + separator);
					outputBuilder.append(numberFormat.format(evaluationsList[i].recall(1)) + separator);
					outputBuilder.append(numberFormat.format(evaluationsList[i].fMeasure(1)) + separator);
					outputBuilder.append(numberFormat.format(evaluationsList[i].areaUnderROC(1)) + separator);
					outputBuilder.append(numberFormat.format(evaluationsList[i].kappa()) + separator);
					outputBuilder.append(numberFormat.format(evaluationsList[i].pctCorrect()/100) + "\n");
					
					outputBuilder.append((k+1) + separator);
					outputBuilder.append(k + separator);
					outputBuilder.append(numberFormat.format(trainingPercentage) + separator);
					outputBuilder.append(numberFormat.format(trainingDefectivePerc) + separator);
					outputBuilder.append(numberFormat.format(testDefectivePerc) + separator);
					outputBuilder.append(classifiersList[i] + separator);
					outputBuilder.append("None" + separator);
					outputBuilder.append("BestFirst" + separator);
					outputBuilder.append("None" + separator);
					outputBuilder.append(numberFormat.format(filteredEvals[i].numTruePositives(1)) + separator);
					outputBuilder.append(numberFormat.format(filteredEvals[i].numFalsePositives(1)) + separator);
					outputBuilder.append(numberFormat.format(filteredEvals[i].numTrueNegatives(1)) + separator);
					outputBuilder.append(numberFormat.format(filteredEvals[i].numFalseNegatives(1)) + separator);
					outputBuilder.append(numberFormat.format(filteredEvals[i].truePositiveRate(1)) + separator);
					outputBuilder.append(numberFormat.format(filteredEvals[i].falsePositiveRate(1)) + separator);
					outputBuilder.append(numberFormat.format(filteredEvals[i].precision(1)) + separator);
					outputBuilder.append(numberFormat.format(filteredEvals[i].recall(1)) + separator);
					outputBuilder.append(numberFormat.format(filteredEvals[i].fMeasure(1)) + separator);
					outputBuilder.append(numberFormat.format(filteredEvals[i].areaUnderROC(1)) + separator);
					outputBuilder.append(numberFormat.format(filteredEvals[i].kappa()) + separator);
					outputBuilder.append(numberFormat.format(filteredEvals[i].pctCorrect()/100) + "\n");
					
					outputBuilder.append((k+1) + separator);
					outputBuilder.append(k + separator);
					outputBuilder.append(numberFormat.format(trainingPercentage) + separator);
					outputBuilder.append(numberFormat.format(trainingDefectivePerc) + separator);
					outputBuilder.append(numberFormat.format(testDefectivePerc) + separator);
					outputBuilder.append(classifiersList[i] + separator);
					outputBuilder.append("Under-Sampling" + separator);
					outputBuilder.append("None" + separator);
					outputBuilder.append("None" + separator);
					outputBuilder.append(numberFormat.format(underSamplingEvals[i].numTruePositives(1)) + separator);
					outputBuilder.append(numberFormat.format(underSamplingEvals[i].numFalsePositives(1)) + separator);
					outputBuilder.append(numberFormat.format(underSamplingEvals[i].numTrueNegatives(1)) + separator);
					outputBuilder.append(numberFormat.format(underSamplingEvals[i].numFalseNegatives(1)) + separator);
					outputBuilder.append(numberFormat.format(underSamplingEvals[i].truePositiveRate(1)) + separator);
					outputBuilder.append(numberFormat.format(underSamplingEvals[i].falsePositiveRate(1)) + separator);
					outputBuilder.append(numberFormat.format(underSamplingEvals[i].precision(1)) + separator);
					outputBuilder.append(numberFormat.format(underSamplingEvals[i].recall(1)) + separator);
					outputBuilder.append(numberFormat.format(underSamplingEvals[i].fMeasure(1)) + separator);
					outputBuilder.append(numberFormat.format(underSamplingEvals[i].areaUnderROC(1)) + separator);
					outputBuilder.append(numberFormat.format(underSamplingEvals[i].kappa()) + separator);
					outputBuilder.append(numberFormat.format(underSamplingEvals[i].pctCorrect()/100) + "\n");
					
					outputBuilder.append((k+1) + separator);
					outputBuilder.append(k + separator);
					outputBuilder.append(numberFormat.format(trainingPercentage) + separator);
					outputBuilder.append(numberFormat.format(trainingDefectivePerc) + separator);
					outputBuilder.append(numberFormat.format(testDefectivePerc) + separator);
					outputBuilder.append(classifiersList[i] + separator);
					outputBuilder.append("Over-Sampling" + separator);
					outputBuilder.append("None" + separator);
					outputBuilder.append("None" + separator);
					outputBuilder.append(numberFormat.format(overSamplingEvals[i].numTruePositives(1)) + separator);
					outputBuilder.append(numberFormat.format(overSamplingEvals[i].numFalsePositives(1)) + separator);
					outputBuilder.append(numberFormat.format(overSamplingEvals[i].numTrueNegatives(1)) + separator);
					outputBuilder.append(numberFormat.format(overSamplingEvals[i].numFalseNegatives(1)) + separator);
					outputBuilder.append(numberFormat.format(overSamplingEvals[i].truePositiveRate(1)) + separator);
					outputBuilder.append(numberFormat.format(overSamplingEvals[i].falsePositiveRate(1)) + separator);
					outputBuilder.append(numberFormat.format(overSamplingEvals[i].precision(1)) + separator);
					outputBuilder.append(numberFormat.format(overSamplingEvals[i].recall(1)) + separator);
					outputBuilder.append(numberFormat.format(overSamplingEvals[i].fMeasure(1)) + separator);
					outputBuilder.append(numberFormat.format(overSamplingEvals[i].areaUnderROC(1)) + separator);
					outputBuilder.append(numberFormat.format(overSamplingEvals[i].kappa()) + separator);
					outputBuilder.append(numberFormat.format(overSamplingEvals[i].pctCorrect()/100) + "\n");
					
					outputBuilder.append((k+1) + separator);
					outputBuilder.append(k + separator);
					outputBuilder.append(numberFormat.format(trainingPercentage) + separator);
					outputBuilder.append(numberFormat.format(trainingDefectivePerc) + separator);
					outputBuilder.append(numberFormat.format(testDefectivePerc) + separator);
					outputBuilder.append(classifiersList[i] + separator);
					outputBuilder.append("SMOTE" + separator);
					outputBuilder.append("None" + separator);
					outputBuilder.append("None" + separator);
					outputBuilder.append(numberFormat.format(smoteSamplingEvals[i].numTruePositives(1)) + separator);
					outputBuilder.append(numberFormat.format(smoteSamplingEvals[i].numFalsePositives(1)) + separator);
					outputBuilder.append(numberFormat.format(smoteSamplingEvals[i].numTrueNegatives(1)) + separator);
					outputBuilder.append(numberFormat.format(smoteSamplingEvals[i].numFalseNegatives(1)) + separator);
					outputBuilder.append(numberFormat.format(smoteSamplingEvals[i].truePositiveRate(1)) + separator);
					outputBuilder.append(numberFormat.format(smoteSamplingEvals[i].falsePositiveRate(1)) + separator);
					outputBuilder.append(numberFormat.format(smoteSamplingEvals[i].precision(1)) + separator);
					outputBuilder.append(numberFormat.format(smoteSamplingEvals[i].recall(1)) + separator);
					outputBuilder.append(numberFormat.format(smoteSamplingEvals[i].fMeasure(1)) + separator);
					outputBuilder.append(numberFormat.format(smoteSamplingEvals[i].areaUnderROC(1)) + separator);
					outputBuilder.append(numberFormat.format(smoteSamplingEvals[i].kappa()) + separator);
					outputBuilder.append(numberFormat.format(smoteSamplingEvals[i].pctCorrect()/100) + "\n");
					
					outputBuilder.append((k+1) + separator);
					outputBuilder.append(k + separator);
					outputBuilder.append(numberFormat.format(trainingPercentage) + separator);
					outputBuilder.append(numberFormat.format(trainingDefectivePerc) + separator);
					outputBuilder.append(numberFormat.format(testDefectivePerc) + separator);
					outputBuilder.append(classifiersList[i] + separator);
					outputBuilder.append("None" + separator);
					outputBuilder.append("None" + separator);
					outputBuilder.append("Threshold" + separator);
					outputBuilder.append(numberFormat.format(sensitiveThresholdEvals[i].numTruePositives(1)) + separator);
					outputBuilder.append(numberFormat.format(sensitiveThresholdEvals[i].numFalsePositives(1)) + separator);
					outputBuilder.append(numberFormat.format(sensitiveThresholdEvals[i].numTrueNegatives(1)) + separator);
					outputBuilder.append(numberFormat.format(sensitiveThresholdEvals[i].numFalseNegatives(1)) + separator);
					outputBuilder.append(numberFormat.format(sensitiveThresholdEvals[i].truePositiveRate(1)) + separator);
					outputBuilder.append(numberFormat.format(sensitiveThresholdEvals[i].falsePositiveRate(1)) + separator);
					outputBuilder.append(numberFormat.format(sensitiveThresholdEvals[i].precision(1)) + separator);
					outputBuilder.append(numberFormat.format(sensitiveThresholdEvals[i].recall(1)) + separator);
					outputBuilder.append(numberFormat.format(sensitiveThresholdEvals[i].fMeasure(1)) + separator);
					outputBuilder.append(numberFormat.format(sensitiveThresholdEvals[i].areaUnderROC(1)) + separator);
					outputBuilder.append(numberFormat.format(sensitiveThresholdEvals[i].kappa()) + separator);
					outputBuilder.append(numberFormat.format(sensitiveThresholdEvals[i].pctCorrect()/100) + "\n");
					
					outputBuilder.append((k+1) + separator);
					outputBuilder.append(k + separator);
					outputBuilder.append(numberFormat.format(trainingPercentage) + separator);
					outputBuilder.append(numberFormat.format(trainingDefectivePerc) + separator);
					outputBuilder.append(numberFormat.format(testDefectivePerc) + separator);
					outputBuilder.append(classifiersList[i] + separator);
					outputBuilder.append("None" + separator);
					outputBuilder.append("None" + separator);
					outputBuilder.append("Learning" + separator);
					outputBuilder.append(numberFormat.format(sensitiveLearningEvals[i].numTruePositives(1)) + separator);
					outputBuilder.append(numberFormat.format(sensitiveLearningEvals[i].numFalsePositives(1)) + separator);
					outputBuilder.append(numberFormat.format(sensitiveLearningEvals[i].numTrueNegatives(1)) + separator);
					outputBuilder.append(numberFormat.format(sensitiveLearningEvals[i].numFalseNegatives(1)) + separator);
					outputBuilder.append(numberFormat.format(sensitiveLearningEvals[i].truePositiveRate(1)) + separator);
					outputBuilder.append(numberFormat.format(sensitiveLearningEvals[i].falsePositiveRate(1)) + separator);
					outputBuilder.append(numberFormat.format(sensitiveLearningEvals[i].precision(1)) + separator);
					outputBuilder.append(numberFormat.format(sensitiveLearningEvals[i].recall(1)) + separator);
					outputBuilder.append(numberFormat.format(sensitiveLearningEvals[i].fMeasure(1)) + separator);
					outputBuilder.append(numberFormat.format(sensitiveLearningEvals[i].areaUnderROC(1)) + separator);
					outputBuilder.append(numberFormat.format(sensitiveLearningEvals[i].kappa()) + separator);
					outputBuilder.append(numberFormat.format(sensitiveLearningEvals[i].pctCorrect()/100) + "\n");
				}
			}
			fileWriter.append(outputBuilder.toString());
			
		} catch (Exception e) {
			Logger logger = Logger.getLogger(WekaTools.class.getName());
			logger.log(Level.SEVERE, "Error in Walk-Forward Analysis", e);
		}
		
	}
	
	private static Integer getInstancesNumberForAttribute(Instances instances, int attributeIndex, int attributeValueIndex) {
		Integer result = 0;
		
		for(Instance instance : instances) {
			if((int)instance.value(attributeIndex) == attributeValueIndex) {
				result++;
			}
		}
		
		return result;
	}
	
	private static Resample getUnderSampling(int majority, int minority) {
		Resample resample = new Resample();
		resample.setBiasToUniformClass(1.0f);
		resample.setNoReplacement(true);
		if(minority == 0) { //training normalizer
			minority = 1;
		}
		if(majority + minority != 0) {
			resample.setSampleSizePercent(2 * 100 * (double) minority / (majority + minority));
		}
		
		return resample;
	}
	
	private static Resample getOverSampling(int majority, int minority) {
		Resample resample = new Resample();
		resample.setBiasToUniformClass(1.0f);
		resample.setNoReplacement(false);
		if(majority + minority != 0) {
			resample.setSampleSizePercent(2 * 100 * (double) majority / (majority + minority));
		}
		
		return resample;
	}
	
	private static SMOTE getSMOTESampling(int majority, int minority) {
		SMOTE smote = new SMOTE();
		if(minority != 0) {
			smote.setPercentage(100 * (double) (majority - minority) / minority);
		}
		
		return smote;
	}
	
	private static CostMatrix createCostMatrix(double weightFalsePositive, double weightFalseNegative) {
	    CostMatrix costMatrix = new CostMatrix(2);
	    costMatrix.setCell(0, 0, 0.0);
	    costMatrix.setCell(1, 0, weightFalsePositive);
	    costMatrix.setCell(0, 1, weightFalseNegative);
	    costMatrix.setCell(1, 1, 0.0);
	    
	    return costMatrix;
	}
}
