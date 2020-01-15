
library(sigclust)
fullpath <- "/home/giray/workspace/RKIDiseaseCluster/rki_data_35140/mutates01-05/"
mutations <- list.files(fullpath,"mutations_")
drugresistances <- list.files(fullpath,"drugresistances_")
nsim <- 2000
nrep <- 1


# for (i in 1:(length(mutations)/2)) {
#   assign(paste("pvalue",mutations[(i*2)-1],sep='_'),sigclust(read.table(paste(fullpath,mutations[(i*2)],sep=""),header=TRUE,sep=" "),nsim=nsim,nrep=nrep,labflag=1,label=read.table(paste(fullpath,mutations[(i*2)-1],sep=""),header=FALSE,sep=" "),icovest=icovest))
# }

for (i in 1:(length(mutations)/2)) {
  dat <- read.table(paste(fullpath,mutations[(i*2)],sep=""),header=TRUE,sep=" ")
  lab  <- read.table(paste(fullpath,mutations[(i*2)-1],sep=""),header=FALSE,sep=" ")
  icovest <- 1
  tryCatch(
    {
      assign(strsplit(paste("pvalue",mutations[(i*2)-1],sep='_'),"_labels.txt")[[1]][1],sigclust(dat,nsim=nsim,nrep=nrep,labflag=1,label=lab,icovest=icovest))     
    },
    error=function(e) {
      icovest <-2
      assign(strsplit(paste("pvalue",mutations[(i*2)-1],sep='_'),"_labels.txt")[[1]][1],sigclust(dat,nsim=nsim,nrep=nrep,labflag=1,label=lab,icovest=icovest))
    })
}
icovest <- 1
for (j in 1:(length(drugresistances)/2)) {
  dat <- read.table(paste(fullpath,drugresistances[(j*2)],sep=""),header=TRUE,sep=" ")
  lab  <- read.table(paste(fullpath,drugresistances[(j*2)-1],sep=""),header=FALSE,sep=" ")
  icovest <- 1
  tryCatch(
    {
      assign(strsplit(paste("pvalue",drugresistances[(j*2)-1],sep='_'),"_labels.txt")[[1]][1],sigclust(dat,nsim=nsim,nrep=nrep,labflag=1,label=lab,icovest=icovest))
    },
    error=function(e) {
      icovest <-2
      assign(strsplit(paste("pvalue",drugresistances[(j*2)-1],sep='_'),"_labels.txt")[[1]][1],sigclust(dat,nsim=nsim,nrep=nrep,labflag=1,label=lab,icovest=icovest))
    })
}


# strsplit(paste("pvalue",mutations[1],sep='_'),"_labels.txt")[[1]][1]