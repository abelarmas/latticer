import seaborn as sns
import matplotlib.pyplot as plt
import pandas as pd
import os

rootdir = 'outputSynthetic'

for dir in os.listdir(rootdir):
    if ".xes" in dir:
        dataFile = "%s/%s/plot.txt" % (rootdir, dir)
        df5 = pd.read_csv(dataFile, sep=',', engine='python')
        
        # plot
        fig = plt.figure()
        #ax = fig.add_subplot(111, projection='3d')
        plt.scatter(df5['Threshold'], df5['CompletenessFactor'], df5['Size'],c=df5['Size'], alpha=0.5)
        #plt.view_init(30, 185)
        cbar= plt.colorbar()
        
        plotFile = "%s/%s/plotSize.png" % (rootdir, dir)
        plt.savefig(plotFile)
