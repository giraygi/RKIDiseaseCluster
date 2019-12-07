          
            library(sigclust)
            
              mu <- 5
              n <- 30
              p <- 500
              # dat <- matrix(rnorm(p*2*n),2*n,p)
              # dat[1:n,1] <- dat[1:n,1]+mu
              # dat[(n+1):(2*n),1] <- dat[(n+1):(2*n),1]-mu
             dat <- read.table("/home/giray/workspace/RKIDiseaseCluster/mutations_union_cluster_35_99.txt",header=TRUE,sep=" ")
            lab  <- read.table("/home/giray/workspace/RKIDiseaseCluster/mutations_union_cluster_35_99_labels1s2s.csv",header=FALSE,sep=" ")
            nsim <- 1000
            nrep <- 1
            icovest <- 3
            pvalue <- sigclust(dat,nsim=nsim,nrep=nrep,labflag=1,label=lab,icovest=icovest)
            #sigclust plot
            plot(pvalue)
                          