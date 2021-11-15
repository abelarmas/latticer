import seaborn as sns
import matplotlib.pyplot as plt
import pandas as pd
import numpy as np
import os

rootdir = 'outputSynthetic'

for dir in os.listdir(rootdir):
    if ".xes" in dir:
        dataFile = "%s/%s/summary.txt" % (rootdir, dir)
        df5 = pd.read_csv(dataFile, sep=',', engine='python')
        
        ax = df5[['#Traces','Threshold','LatticeCount','Min.Var','Max.Var','Avg.Var']].plot.bar(stacked=False, rot=0, x='Threshold')
        ax.legend(title='Labels', bbox_to_anchor=(1.05, 1), loc=2, borderaxespad=0.)
        
        plotFile = "%s/%s/tracesLatticeVariants.png" % (rootdir, dir)
        ax.figure.savefig(plotFile, format='png', dpi=100,bbox_inches='tight')

        ax = df5[['Threshold','LatticeCount','%MinComp','%MaxComp','%AvgComp']].plot.bar(stacked=False, rot=0, x='Threshold')
        ax.legend(title='Labels', bbox_to_anchor=(1.05, 1), loc=2, borderaxespad=0.)

        plotFile = "%s/%s/tracesCompleteness.png" % (rootdir, dir)
        ax.figure.savefig(plotFile, format='png', dpi=100,bbox_inches='tight')
