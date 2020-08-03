
library(tidyverse)
library(caret)
library(glmnet)
library("stringr")
library(dplyr)
library(MLmetrics)

directory.path <- "/home/giray/glm_final/mo1a_fullmutationloglink/"
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
      # print("sample_n for inspecting the data")
      # print(sample_n(centralities, 3))
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
      
      
      # print("standard glmnet output")
      # print(glmnet(x, y, family = family, alpha = 1, lambda = NULL))
      
      
      
      library(glmnet)
      # Find the best lambda using cross-validation
      set.seed(123)
      cv.lasso <- cv.glmnet(x, y, alpha = 1, family = family)
      # Fit the final model on the training data
      model <- glmnet(x, y, alpha = 1, family = family,
                      lambda = cv.lasso$lambda.min)
      print("--------------------------------------")
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
      # print("model accuracy based on MAE standard model")
      # print(1-mean(abs(predicted.classes - observed.classes))/mean(observed.classes))
      # print("model accuracy based on RMSE standard model")
      # print(1-RMSE(predicted.classes,observed.classes)/mean(observed.classes))
      # print("MAE")
      # print(MAE(predicted.classes,observed.classes))
      # print("MSE")
      # print(MSE(predicted.classes,observed.classes))
      # print("RMSE")
      # print(RMSE(predicted.classes,observed.classes))
      # print("R2 Score")
      # print(R2_Score(predicted.classes,observed.classes))
      # print("RAE")
      # print(RAE(predicted.classes,observed.classes))
      # print("RRSE")
      # print(RRSE(predicted.classes,observed.classes))
      # print("accuracy")
      # print(Accuracy(predicted.classes,observed.classes))
      # tLL <- model$nulldev - deviance(model)
      # k <- model$df
      # n <- model$nobs
      # AICc <- -tLL+2*k+2*k*(k+1)/(n-k-1)
      # print("AICc")
      # print(AICc)
      # 
      # BICc<-log(n)*k - tLL
      # print("BICc")
      # print(BICc)
      # 
      # AIC <- n*log(MSE(predicted.classes,observed.classes))+2*k
      # print("AIC MSE")
      # print(AIC)
      # 
      # BIC <- n*log(MSE(predicted.classes,observed.classes))+k*log(n)
      # print("BIC MSE")
      # print(BIC)
      
       print("--------------------------------------")
      
      library(glmnet)
      set.seed(123)
      
      if(output!="no_of_full_mutations")
        cv.lasso <- cv.glmnet(x, y, alpha = 1, family = family)
      else{
        if (family=="gaussian")
          cv.lasso <- cv.glmnet(x, y, alpha = 1, family = gaussian(link="log"))
        else
        if (family=="poisson")
          cv.lasso <- cv.glmnet(x, y, alpha = 1, family = poisson(link="log"))
        plot(cv.lasso)
      }
        
      
      print("lambda min")
      print(cv.lasso$lambda.min)
      print("lambda 1se")
      print(cv.lasso$lambda.1se)
      print("coefficients with lambda min")
      print(coef(cv.lasso, cv.lasso$lambda.min))
      print("cofficients with lambda 1se")
      print(coef(cv.lasso, cv.lasso$lambda.1se))
      
      # Final model with lambda.min
      if(output!="no_of_full_mutations")
        lasso.model <- glmnet(x, y, alpha = 1, family = family,
                              lambda = cv.lasso$lambda.min)
      else{
        if (family=="gaussian")
          lasso.model <- glmnet(x, y, alpha = 1, family = gaussian(link="log"),
                                lambda = cv.lasso$lambda.min)
        else if (family=="poisson")
            lasso.model <- glmnet(x, y, alpha = 1, family = poisson(link="log"),
                                  lambda = cv.lasso$lambda.min)
      }
       
      # Make prediction on test data
      x.test <- model.matrix(as.formula(paste(output, ".", sep="~")), test.data)[,-1]
      probabilities <- lasso.model %>% predict(newx = x.test, type = "response")
      # predicted.classes <- ifelse(probabilities > 0.5, "pos", "neg")
      predicted.classes <- round(probabilities)
      # Model accuracy
      observed.classes <- test.data[,output]
      print("mean of observed classes")
      print(mean(observed.classes))
      # print("vif")
      # print(car::vif(lasso.model))
      print("--------------------------------------")
      print("coefficients of the lasso model")
      print(coef(lasso.model))
      print("model accuracy based on MAE lasso 1")
      print(1-mean(abs(predicted.classes - observed.classes))/mean(observed.classes))
      print("model accuracy based on RMSE lasso 1")
      print(1-RMSE(predicted.classes,observed.classes)/mean(observed.classes))
      print("MAE")
      print(MAE(predicted.classes,observed.classes))
      print("MSE")
      print(MSE(predicted.classes,observed.classes))
      print("RMSE")
      print(RMSE(predicted.classes,observed.classes))
      print("R2 Score")
      print(R2_Score(predicted.classes,observed.classes))
      print("RAE")
      print(RAE(predicted.classes,observed.classes))
      print("RRSE")
      print(RRSE(predicted.classes,observed.classes))
      print("accuracy")
      print(Accuracy(predicted.classes,observed.classes))
      
      tLL <- lasso.model$nulldev - deviance(lasso.model)
      k <- lasso.model$df
      n <- lasso.model$nobs
      AICc <- -tLL+2*k+2*k*(k+1)/(n-k-1)
      print("AICc")
      print(AICc)
      
      BICc<-log(n)*k - tLL
      print("BICc")
      print(BICc)
      
      AIC <- n*log(MSE(predicted.classes,observed.classes))+2*k
      print("AIC MSE")
      print(AIC)
      
      BIC <- n*log(MSE(predicted.classes,observed.classes))+k*log(n)
      print("BIC MSE")
      print(BIC)
      
      print("--------------------------------------")
      
      
      # Final model with lambda.1se
      if(output!="no_of_full_mutations")
        lasso2.model <- glmnet(x, y, alpha = 1, family = family,
                               lambda = cv.lasso$lambda.1se)
      else{
        if (family=="gaussian")
          lasso2.model <- glmnet(x, y, alpha = 1, family = gaussian(link="log"),
                                 lambda = cv.lasso$lambda.1se)
        else if (family=="poisson")
          lasso2.model <- glmnet(x, y, alpha = 1, family = poisson(link="log"),
                                 lambda = cv.lasso$lambda.1se)
      }
        
      # Make prediction on test data
      x.test <- model.matrix(as.formula(paste(output, ".", sep="~")), test.data)[,-1]
      probabilities <- lasso2.model %>% predict(newx = x.test, type = "response")
      # predicted.classes <- ifelse(probabilities > 0.5, "pos", "neg")
      predicted.classes <- round(probabilities)
      # Model accuracy rate
      observed.classes <- test.data[,output]
      # print("vif")
      # print(car::vif(lasso2.model))
      print("coefficients of the lasso2 model")
      print(coef(lasso2.model))
      print("model accuracy based on MAE lasso 2")
      print(1-mean(abs(predicted.classes - observed.classes))/mean(observed.classes))
      print("model accuracy based on RMSE lasso 2")
      print(1-RMSE(predicted.classes,observed.classes)/mean(observed.classes))
      print("MAE")
      print(MAE(predicted.classes,observed.classes))
      print("MSE")
      print(MSE(predicted.classes,observed.classes))
      print("RMSE")
      print(RMSE(predicted.classes,observed.classes))
      print("R2 Score")
      print(R2_Score(predicted.classes,observed.classes))
      print("RAE")
      print(RAE(predicted.classes,observed.classes))
      print("RRSE")
      print(RRSE(predicted.classes,observed.classes))
      print("accuracy")
      print(Accuracy(predicted.classes,observed.classes))
      
      tLL <- lasso2.model$nulldev - deviance(lasso2.model)
      k <- lasso2.model$df
      n <- lasso2.model$nobs
      AICc <- -tLL+2*k+2*k*(k+1)/(n-k-1)
      print("AICc")
      print(AICc)
      
      BICc<-log(n)*k - tLL
      print("BICc")
      print(BICc)
      
      AIC <- n*log(MSE(predicted.classes,observed.classes))+2*k
      print("AIC MSE")
      print(AIC)
      
      BIC <- n*log(MSE(predicted.classes,observed.classes))+k*log(n)
      print("BIC MSE")
      print(BIC)
      
      print("--------------------------------------")
      
      # Fit the model
      if(output!="no_of_full_mutations")
        full.model <- glm(as.formula(paste(output, ".", sep="~")), data = train.data, family = family)
      else{
        if (family=="gaussian")
          full.model <- glm(as.formula(paste(output, ".", sep="~")), data = train.data, family = gaussian(link="log"))
        else if (family == "poisson")
          full.model <- glm(as.formula(paste(output, ".", sep="~")), data = train.data, family = poisson(link="log"))
      }
      
      # Make predictions
      probabilities <- full.model %>% predict(test.data, type = "response")
      # predicted.classes <- ifelse(probabilities > 0.5, "pos", "neg")
      predicted.classes <- round(probabilities)
      # Model accuracy
      observed.classes <- test.data[,output]
      print("coefficients of the full model")
      print(coef(full.model))
      print("model accuracy based on MAE full model")
      print(1-mean(abs(predicted.classes - observed.classes))/mean(observed.classes))
      print("model accuracy based on RMSE full model")
      print(1-RMSE(predicted.classes,observed.classes)/mean(observed.classes))
      print("MAE")
      print(MAE(predicted.classes,observed.classes))
      print("MSE")
      print(MSE(predicted.classes,observed.classes))
      print("RMSE")
      print(RMSE(predicted.classes,observed.classes))
      print("R2 Score")
      print(R2_Score(predicted.classes,observed.classes))
      print("RAE")
      print(RAE(predicted.classes,observed.classes))
      print("RRSE")
      print(RRSE(predicted.classes,observed.classes))
      print("accuracy")
      print(Accuracy(predicted.classes,observed.classes))
      tLL <- full.model$null.deviance - deviance(full.model)
      k <- full.model$df.null - full.model$df.residual
      n <- length(full.model$residuals)
      AICc <- -tLL+2*k+2*k*(k+1)/(n-k-1)
      print("AICc")
      print(AICc)
      
      BICc<-log(n)*k - tLL
      print("BICc")
      print(BICc)
      
      AIC <- n*log(MSE(predicted.classes,observed.classes))+2*k
      print("AIC MSE")
      print(AIC)
      
      BIC <- n*log(MSE(predicted.classes,observed.classes))+k*log(n)
      print("BIC MSE")
      print(BIC)
      
      print("--------------------------------------")
      
      
      print("linear")
      print(ld.vars <- attributes(alias(full.model)$Complete)$dimnames[[1]])
      print("vif")
      print(car::vif(full.model))
      
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