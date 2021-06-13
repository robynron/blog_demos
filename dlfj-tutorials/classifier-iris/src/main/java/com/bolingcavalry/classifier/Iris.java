/* *****************************************************************************
 * Copyright (c) 2020 Konduit K.K.
 * Copyright (c) 2015-2019 Skymind, Inc.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Apache License, Version 2.0 which is available at
 * https://www.apache.org/licenses/LICENSE-2.0.
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 *
 * SPDX-License-Identifier: Apache-2.0
 ******************************************************************************/

package com.bolingcavalry.classifier;

import com.bolingcavalry.commons.utils.DownloaderUtility;
import lombok.extern.slf4j.Slf4j;
import org.datavec.api.records.reader.RecordReader;
import org.datavec.api.records.reader.impl.csv.CSVRecordReader;
import org.datavec.api.split.FileSplit;
import org.deeplearning4j.datasets.datavec.RecordReaderDataSetIterator;
import org.deeplearning4j.nn.conf.MultiLayerConfiguration;
import org.deeplearning4j.nn.conf.NeuralNetConfiguration;
import org.deeplearning4j.nn.conf.layers.DenseLayer;
import org.deeplearning4j.nn.conf.layers.OutputLayer;
import org.deeplearning4j.nn.multilayer.MultiLayerNetwork;
import org.deeplearning4j.nn.weights.WeightInit;
import org.deeplearning4j.optimize.listeners.ScoreIterationListener;
import org.nd4j.evaluation.classification.Evaluation;
import org.nd4j.linalg.activations.Activation;
import org.nd4j.linalg.api.ndarray.INDArray;
import org.nd4j.linalg.dataset.DataSet;
import org.nd4j.linalg.dataset.SplitTestAndTrain;
import org.nd4j.linalg.dataset.api.iterator.DataSetIterator;
import org.nd4j.linalg.dataset.api.preprocessor.DataNormalization;
import org.nd4j.linalg.dataset.api.preprocessor.NormalizerStandardize;
import org.nd4j.linalg.learning.config.Sgd;
import org.nd4j.linalg.lossfunctions.LossFunctions;

import java.io.File;

/**
 * @author will (zq2599@gmail.com)
 * @version 1.0
 * @description: 鸢尾花训练
 * @date 2021/6/13 17:30
 */
@SuppressWarnings("DuplicatedCode")
@Slf4j
public class Iris {

    public static void main(String[] args) throws  Exception {

        //First: get the dataset using the record reader. CSVRecordReader handles loading/parsing
        //第一阶段：准备

        // 跳过的行数，因为可能是表头
        int numLinesToSkip = 0;
        // 分隔符
        char delimiter = ',';

        // CSV读取工具
        RecordReader recordReader = new CSVRecordReader(numLinesToSkip,delimiter);

        // 下载并解压后，得到文件的位置
        String dataPathLocal = DownloaderUtility.IRISDATA.Download();

        log.info("鸢尾花数据已下载并解压至 : {}", dataPathLocal);

        // 读取下载后的文件
        recordReader.initialize(new FileSplit(new File(dataPathLocal,"iris.txt")));

        //Second: the RecordReaderDataSetIterator handles conversion to DataSet objects, ready for use in neural network

        // 每一行的内容大概是这样的：5.1,3.5,1.4,0.2,0

        // 一共五个字段，从零开始算的话，标签在第四个字段
        int labelIndex = 4;

        // 鸢尾花一共分为三类
        int numClasses = 3;

        // 一共150个样本
        int batchSize = 150;    //Iris data set: 150 examples total. We are loading all of them into one DataSet (not recommended for large data sets)

        // 加载到数据集迭代器中
        DataSetIterator iterator = new RecordReaderDataSetIterator(recordReader,batchSize,labelIndex,numClasses);

        DataSet allData = iterator.next();

        // 洗牌（打乱顺序）
        allData.shuffle();

        // 设定比例，150个样本中，百分之六十五用于训练
        SplitTestAndTrain testAndTrain = allData.splitTestAndTrain(0.65);  //Use 65% of data for training

        // 训练用的数据集
        DataSet trainingData = testAndTrain.getTrain();

        // 验证用的数据集
        DataSet testData = testAndTrain.getTest();

        // We need to normalize our data. We'll use NormalizeStandardize (which gives us mean 0, unit variance):

        // 指定归一化器：独立地将每个特征值（和可选的标签值）归一化为0平均值和1的标准差。
        DataNormalization normalizer = new NormalizerStandardize();

        // 先拟合
        normalizer.fit(trainingData);

        // 对训练集做归一化
        normalizer.transform(trainingData);

        // 对测试集做归一化
        normalizer.transform(testData);

        // 每个鸢尾花有四个特征
        final int numInputs = 4;

        // 公有三种鸢尾花
        int outputNum = 3;

        // 随机数种子
        long seed = 6;

        //第二阶段：训练
        log.info("Build model....");
        MultiLayerConfiguration conf = new NeuralNetConfiguration.Builder()
            .seed(seed)
            .activation(Activation.TANH)       // 激活函数选用标准的tanh(双曲正切)
            .weightInit(WeightInit.XAVIER)     // 权重初始化选用XAVIER：均值 0, 方差为 2.0/(fanIn + fanOut)的高斯分布
            .updater(new Sgd(0.1))  // 更新器，设置SGD学习速率调度器
            .l2(1e-4)                          // L2正则化配置
            .list()                            // 将所有超参数配置添加到列表中
            .layer(new DenseLayer.Builder().nIn(numInputs).nOut(3)  //
                .build())
            .layer(new DenseLayer.Builder().nIn(3).nOut(3)
                .build())
            .layer( new OutputLayer.Builder(LossFunctions.LossFunction.NEGATIVELOGLIKELIHOOD)
                .activation(Activation.SOFTMAX) //Override the global TANH activation with softmax for this layer
                .nIn(3).nOut(outputNum).build())
            .build();

        //run the model
        MultiLayerNetwork model = new MultiLayerNetwork(conf);
        model.init();
        //record score once every 100 iterations
        model.setListeners(new ScoreIterationListener(100));

        for(int i=0; i<1000; i++ ) {
            model.fit(trainingData);
        }

        // 第三阶段：评估
        //evaluate the model on the test set
        Evaluation eval = new Evaluation(3);
        INDArray output = model.output(testData.getFeatures());
        eval.eval(testData.getLabels(), output);
        log.info(eval.stats());
    }

}
