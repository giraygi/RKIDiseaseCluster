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
      
      directory <-strsplit(fullpath,"/")
      mutationsdirectory <- paste("outputmutations_",directory[[1]][length(directory[[1]])],".csv",sep = "")
      resistancesdirectory <- paste("outputresistances_",directory[[1]][length(directory[[1]])],".csv",sep="")
              
              for (k in 1:(length(mutations)/2)){
                
                if(k==1){
                  print(c("pair","icovest","xcindex","pval","pvalnorm","simbackvar"))
                  write.table(t(c("pair","icovest","xcindex","pval","pvalnorm","simbackvar")),file=mutationsdirectory,col.names= FALSE,append = TRUE, quote=FALSE)
                }
                # strsplit(mutations[2*k],sep="mutations_")[1]
                if(class(get(strsplit(paste("pvalue",mutations[(k*2)-1],sep='_'),"_labels.txt")[[1]][1]))=="sigclust"){
                  print(c(sub(mutations[2*k],pattern='mutations_',replacement = ''),get(strsplit(paste("pvalue",mutations[(k*2)-1],sep='_'),"_labels.txt")[[1]][1])@icovest,get(strsplit(paste("pvalue",mutations[(k*2)-1],sep='_'),"_labels.txt")[[1]][1])@xcindex,get(strsplit(paste("pvalue",mutations[(k*2)-1],sep='_'),"_labels.txt")[[1]][1])@pval,get(strsplit(paste("pvalue",mutations[(k*2)-1],sep='_'),"_labels.txt")[[1]][1])@pvalnorm,get(strsplit(paste("pvalue",mutations[(k*2)-1],sep='_'),"_labels.txt")[[1]][1])@simbackvar))
                  write.table(t(c(sub(mutations[2*k],pattern='mutations_',replacement = ''),get(strsplit(paste("pvalue",mutations[(k*2)-1],sep='_'),"_labels.txt")[[1]][1])@icovest,get(strsplit(paste("pvalue",mutations[(k*2)-1],sep='_'),"_labels.txt")[[1]][1])@xcindex,get(strsplit(paste("pvalue",mutations[(k*2)-1],sep='_'),"_labels.txt")[[1]][1])@pval,get(strsplit(paste("pvalue",mutations[(k*2)-1],sep='_'),"_labels.txt")[[1]][1])@pvalnorm,get(strsplit(paste("pvalue",mutations[(k*2)-1],sep='_'),"_labels.txt")[[1]][1])@simbackvar)),file=mutationsdirectory,col.names= FALSE,append = TRUE, quote=FALSE)
                }
                else {
                  print("error")
                  write.table(t(c(sub(mutations[2*k],pattern='mutations_',replacement = ''),"e","e","e","e","e")),file=mutationsdirectory,col.names= FALSE,append = TRUE, quote=FALSE)
                }
              }
          print("--------")
          
              for (l in 1:(length(drugresistances)/2)){
                
                if(l==1){
                  print(c("pair","icovest","xcindex","pval","pvalnorm","simbackvar"))
                  write.table(t(c("pair","icovest","xcindex","pval","pvalnorm","simbackvar")),file=resistancesdirectory,col.names= FALSE,append = TRUE, quote=FALSE)
                }
                
                # strsplit(drugresistances[2*l],sep="drugresistances_")[1],
                
                if(class(get(strsplit(paste("pvalue",drugresistances[(l*2)-1],sep='_'),"_labels.txt")[[1]][1]))=="sigclust"){
                  print(c(sub(drugresistances[2*l],pattern='drugresistances_',replacement = ''),get(strsplit(paste("pvalue",drugresistances[(l*2)-1],sep='_'),"_labels.txt")[[1]][1])@icovest,get(strsplit(paste("pvalue",drugresistances[(l*2)-1],sep='_'),"_labels.txt")[[1]][1])@xcindex,get(strsplit(paste("pvalue",drugresistances[(l*2)-1],sep='_'),"_labels.txt")[[1]][1])@pval,get(strsplit(paste("pvalue",drugresistances[(l*2)-1],sep='_'),"_labels.txt")[[1]][1])@pvalnorm,get(strsplit(paste("pvalue",drugresistances[(l*2)-1],sep='_'),"_labels.txt")[[1]][1])@simbackvar))
                  write.table(t(c(sub(drugresistances[2*l],pattern='drugresistances_',replacement = ''),get(strsplit(paste("pvalue",drugresistances[(l*2)-1],sep='_'),"_labels.txt")[[1]][1])@icovest,get(strsplit(paste("pvalue",drugresistances[(l*2)-1],sep='_'),"_labels.txt")[[1]][1])@xcindex,get(strsplit(paste("pvalue",drugresistances[(l*2)-1],sep='_'),"_labels.txt")[[1]][1])@pval,get(strsplit(paste("pvalue",drugresistances[(l*2)-1],sep='_'),"_labels.txt")[[1]][1])@pvalnorm,get(strsplit(paste("pvalue",drugresistances[(l*2)-1],sep='_'),"_labels.txt")[[1]][1])@simbackvar)),file=resistancesdirectory,col.names= FALSE,append = TRUE, quote=FALSE)
                }
                else {
                  print("error")
                  write.table(t(c(sub(drugresistances[2*l],pattern='drugresistances_',replacement = ''),"e","e","e","e","e")),file=resistancesdirectory,col.names= FALSE,append = TRUE, quote=FALSE)
                }
              }