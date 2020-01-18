    library(sigclust)
    fullpath <- "/media/giray/Windows/Nextcloud/transmission networks/data/rki_data_35140/differsfull-05/"
    mutations <- list.files(fullpath,"mutations_")
    drugresistances <- list.files(fullpath,"drugresistances_")
    nsim <- 2000
    nrep <- 1
    
    for (i in 1:(length(mutations)/2)) {
      dat <- read.table(paste(fullpath,mutations[(i*2)],sep=""),header=TRUE,sep=" ")
      lab  <- read.table(paste(fullpath,mutations[(i*2)-1],sep=""),header=FALSE,sep=" ")
      icovest <- 1
    errorstatus<-try(assign(strsplit(paste("pvalue",mutations[(i*2)-1],sep='_'),"_labels.txt")[[1]][1],sigclust(dat,nsim=nsim,nrep=nrep,labflag=1,label=lab,icovest=icovest))     )
          if(class(errorstatus)=="try-error")
          {
            icovest <-2
            errorstatus2<-try(assign(strsplit(paste("pvalue",mutations[(i*2)-1],sep='_'),"_labels.txt")[[1]][1],sigclust(dat,nsim=nsim,nrep=nrep,labflag=1,label=lab,icovest=icovest)))
            if(class(errorstatus2)=="try-error"){
              icovest <-3
              errorstatus3<-try(assign(strsplit(paste("pvalue",mutations[(i*2)-1],sep='_'),"_labels.txt")[[1]][1],sigclust(dat,nsim=nsim,nrep=nrep,labflag=1,label=lab,icovest=icovest)))
              if(class(errorstatus3)=="try-error"){
                assign(strsplit(paste("pvalue",mutations[(i*2)-1],sep='_'),"_labels.txt")[[1]][1],i)
                next}
            }
          }
    }
    icovest <- 1
    for (j in 1:(length(drugresistances)/2)) {
      dat <- read.table(paste(fullpath,drugresistances[(j*2)],sep=""),header=TRUE,sep=" ")
      lab  <- read.table(paste(fullpath,drugresistances[(j*2)-1],sep=""),header=FALSE,sep=" ")
      icovest <- 1
      errorstatus<-try(assign(strsplit(paste("pvalue",drugresistances[(j*2)-1],sep='_'),"_labels.txt")[[1]][1],sigclust(dat,nsim=nsim,nrep=nrep,labflag=1,label=lab,icovest=icovest)))
      if(class(errorstatus)=="try-error"){
        icovest <-2
        errorstatus2<-try(assign(strsplit(paste("pvalue",drugresistances[(j*2)-1],sep='_'),"_labels.txt")[[1]][1],sigclust(dat,nsim=nsim,nrep=nrep,labflag=1,label=lab,icovest=icovest)))
        if(class(errorstatus2)=="try-error"){
          icovest <-3
          errorstatus3<-try(assign(strsplit(paste("pvalue",drugresistances[(j*2)-1],sep='_'),"_labels.txt")[[1]][1],sigclust(dat,nsim=nsim,nrep=nrep,labflag=1,label=lab,icovest=icovest)))
          if(class(errorstatus3)=="try-error"){
            assign(strsplit(paste("pvalue",drugresistances[(j*2)-1],sep='_'),"_labels.txt")[[1]][1],j)
            next}
        }
      }
    }

    for (k in 1:(length(mutations)/2)){
      if(class(get(strsplit(paste("pvalue",mutations[(k*2)-1],sep='_'),"_labels.txt")[[1]][1]))=="sigclust")
        print(c(get(strsplit(paste("pvalue",mutations[(k*2)-1],sep='_'),"_labels.txt")[[1]][1])@icovest,get(strsplit(paste("pvalue",mutations[(k*2)-1],sep='_'),"_labels.txt")[[1]][1])@xcindex,get(strsplit(paste("pvalue",mutations[(k*2)-1],sep='_'),"_labels.txt")[[1]][1])@pval))
      else
        print("error")
    }
    
    for (l in 1:(length(drugresistances)/2)){
      if(class(get(strsplit(paste("pvalue",drugresistances[(l*2)-1],sep='_'),"_labels.txt")[[1]][1]))=="sigclust")
        print(c(get(strsplit(paste("pvalue",drugresistances[(l*2)-1],sep='_'),"_labels.txt")[[1]][1])@icovest,get(strsplit(paste("pvalue",drugresistances[(l*2)-1],sep='_'),"_labels.txt")[[1]][1])@xcindex,get(strsplit(paste("pvalue",drugresistances[(l*2)-1],sep='_'),"_labels.txt")[[1]][1])@pval))
      else
        print("error")
    }
    

    