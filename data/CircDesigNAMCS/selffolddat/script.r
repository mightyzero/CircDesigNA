# Set working directory with setwd() prior to calling script.
DNA <- read.table("dna.txt",header=TRUE)
RNA <- read.table("rna.txt",header=TRUE)

# If you'd like to graph it,
library(ggplot2)
library(Cairo)
g <- ggplot(data=RNA,aes(x=N,y=mfeNoDiag))
g <- g + geom_point()
g <- g + stat_smooth(method="lm")
g <- g + opts(panel.background=theme_blank())
# Strangely, this call makes figures look MUCH better
g <- g + theme_bw()
# Remove axis titles
g <- g + labs(x=NULL,y=NULL)
# Now we produce an SVG file.
CairoSVG("LinearModelGraph.svg", width=7, height=7)
print(g)
dev.off()

model_DNA <- lm(DNA$mfeNoDiag ~ DNA$N)
model_RNA <- lm(RNA$mfeNoDiag ~ RNA$N)

model_DNA
model_RNA