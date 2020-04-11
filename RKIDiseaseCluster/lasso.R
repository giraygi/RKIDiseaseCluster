library(tidyverse)
library(caret)
library(glmnet)

directory.path <- "/home/giray/Nextcloud/transmission networks/data/lasso/multiple output/"
csv.files <- list.files(directory.path,pattern = "\\.csv$")

for (i in 1:length(csv.files)){
 for (j in 1:2){
   for (k in 1:3){
      
# i<-1
# j<-1
# k<-3
      file.name <-csv.files[i]
      centralities <- read.table(paste0(directory.path,file.name,""),header=TRUE,sep=",")
      family <- switch(j,"gaussian", "poisson","mgaussian","multinomial","binomial","cox")
      output <- switch(k,"noof_mutations","noof_resistances","no_of_full_mutations") 
      pdf(paste0(directory.path,str_remove(file.name,".csv"),"_",family,"_",output,".pdf",""))
      sink(paste0(directory.path,str_remove(file.name,".csv"),"_",family,"_",output,".out",""))
      if(output!="noof_mutations" && output!="noof_resistances" && output!="no_of_full_mutations"){
        stop("output parameter should be chosen from noof_mutations/noof_resistances/no_of_full_mutations")
      } else {
        if(output!="noof_mutations")
          centralities$noof_mutations <- NULL
        if(output!="noof_resistances")
          centralities$noof_resistances <- NULL
        if(output!="no_of_full_mutations")
          centralities$no_of_full_mutations <- NULL
      }
      print("sample_n for inspecting the data")
      # Inspect the data
      print(sample_n(centralities, 3))
      # Split the data into training and test set
      set.seed(123)
      training.samples <- centralities[,output] %>% 
        createDataPartition(p = 0.8, list = FALSE)
      train.data  <- centralities[training.samples, ]
      test.data <- centralities[-training.samples, ]
      
      
      
      # Dumy code categorical predictor variables
      x <- model.matrix(as.formula(paste(output, ".", sep="~")), train.data)[,-1]
      # Convert the outcome (class) to a numerical variable
      # y <- ifelse(train.data[,output] == "pos", 1, 0)
      y <- train.data[,output] 
      
      
      print("standard glmnet output")
      print(glmnet(x, y, family = family, alpha = 1, lambda = NULL))
      
      
      
      library(glmnet)
      # Find the best lambda using cross-validation
      set.seed(123) 
      cv.lasso <- cv.glmnet(x, y, alpha = 1, family = family)
      # Fit the final model on the training data
      model <- glmnet(x, y, alpha = 1, family = family,
                      lambda = cv.lasso$lambda.min)
      # Display regression coefficients
      print("coefficients with standard model")
      print(coef(model))
      # Make predictions on the test data
      x.test <- model.matrix(as.formula(paste(output, ".", sep="~")), test.data)[,-1]
      probabilities <- model %>% predict(newx = x.test, type = "response")
      # predicted.classes <- ifelse(probabilities > 0.5, "pos", "neg")
      predicted.classes <- round(probabilities)
      # Model accuracy
      observed.classes <- test.data[,output]
      print("model accuracy lasso 1.0")
      print(1-mean(abs(predicted.classes - observed.classes))/mean(observed.classes))
      
      library(glmnet)
      set.seed(123)
      cv.lasso <- cv.glmnet(x, y, alpha = 1, family = family)
      plot(cv.lasso)
      
      print("lambda min")
      print(cv.lasso$lambda.min)
      print("lambda 1se")
      print(cv.lasso$lambda.1se)
      print("coefficients with lambda min")
      print(coef(cv.lasso, cv.lasso$lambda.min))
      print("cofficients with lambda 1se")
      print(coef(cv.lasso, cv.lasso$lambda.1se))
      
      # Final model with lambda.min
      lasso.model <- glmnet(x, y, alpha = 1, family = family,
                            lambda = cv.lasso$lambda.min)
      # Make prediction on test data
      x.test <- model.matrix(as.formula(paste(output, ".", sep="~")), test.data)[,-1]
      probabilities <- lasso.model %>% predict(newx = x.test, type = "response")
      # predicted.classes <- ifelse(probabilities > 0.5, "pos", "neg")
      predicted.classes <- round(probabilities)
      # Model accuracy
      observed.classes <- test.data[,output]
      print("coefficients of the lasso model")
      print(coef(lasso.model))
      print("model accuracy lasso 1.1")
      print(1-mean(abs(predicted.classes - observed.classes))/mean(observed.classes))
      
      # Final model with lambda.1se
      lasso2.model <- glmnet(x, y, alpha = 1, family = family,
                             lambda = cv.lasso$lambda.1se)
      # Make prediction on test data
      x.test <- model.matrix(as.formula(paste(output, ".", sep="~")), test.data)[,-1]
      probabilities <- lasso2.model %>% predict(newx = x.test, type = "response")
      # predicted.classes <- ifelse(probabilities > 0.5, "pos", "neg")
      predicted.classes <- round(probabilities)
      # Model accuracy rate
      observed.classes <- test.data[,output]
      print("coefficients of the lasso2 model")
      print(coef(lasso2.model))
      print("model accuracy lasso 2")
      print(1-mean(abs(predicted.classes - observed.classes))/mean(observed.classes))
      
      # Fit the model
      full.model <- glm(as.formula(paste(output, ".", sep="~")), data = train.data, family = family)
      # Make predictions
      probabilities <- full.model %>% predict(test.data, type = "response")
      # predicted.classes <- ifelse(probabilities > 0.5, "pos", "neg")
      predicted.classes <- round(probabilities)
      # Model accuracy
      observed.classes <- test.data[,output]
      print("coefficients of the full model")
      print(coef(full.model))
      print("model accuracy full model")
      print(1-mean(abs(predicted.classes - observed.classes))/mean(observed.classes))
      
      # test değeri F de olabilir. İkinci model parametresi istenen tipte olmadığı için bir karşılaştırma yapmıyor ve tek modelliden farklı bir sonuç üretmiyor.
      print(anova(full.model, cv.lasso, test = "Chisq"))
      
      print(summary(full.model))
      
      predicted.and.compared <-cbind(observed.classes,predicted.classes)
      
      plot(full.model)
      
      sink()
      
      dev.off()
      
   }
 }
}
