  library(tidyverse)
  library(caret)
  library(glmnet)
  
  
  
  # Load the data and remove NAs
  # data("PimaIndiansDiabetes2", package = "mlbench")
  # PimaIndiansDiabetes2 <- na.omit(PimaIndiansDiabetes2)
  
   PimaIndiansDiabetes2 <- read.table("/home/giray/workspace/RKIDiseaseCluster/yedek/nusretshortpowerless.csv",header=TRUE,sep=",")
  # Inspect the data
  sample_n(PimaIndiansDiabetes2, 3)
  # Split the data into training and test set
  set.seed(123)
  training.samples <- PimaIndiansDiabetes2$noof_mutations %>% 
    createDataPartition(p = 0.8, list = FALSE)
  train.data  <- PimaIndiansDiabetes2[training.samples, ]
  test.data <- PimaIndiansDiabetes2[-training.samples, ]
  
  
  
  # Dumy code categorical predictor variables
  x <- model.matrix(noof_mutations~., train.data)[,-1]
  # Convert the outcome (class) to a numerical variable
  # y <- ifelse(train.data$noof_mutations == "pos", 1, 0)
  y <- train.data$noof_mutations 
  
  
  
  glmnet(x, y, family = "gaussian", alpha = 1, lambda = NULL)
  
  
  
  library(glmnet)
  # Find the best lambda using cross-validation
  set.seed(123) 
  cv.lasso <- cv.glmnet(x, y, alpha = 1, family = "gaussian")
  # Fit the final model on the training data
  model <- glmnet(x, y, alpha = 1, family = "gaussian",
                  lambda = cv.lasso$lambda.min)
  # Display regression coefficients
  coef(model)
  # Make predictions on the test data
  x.test <- model.matrix(noof_mutations ~., test.data)[,-1]
  probabilities <- model %>% predict(newx = x.test)
  # predicted.classes <- ifelse(probabilities > 0.5, "pos", "neg")
  predicted.classes <- probabilities
  # Model accuracy
  observed.classes <- test.data$noof_mutations
  mean(predicted.classes == observed.classes)
  
  
  
  library(glmnet)
  set.seed(123)
  cv.lasso <- cv.glmnet(x, y, alpha = 1, family = "gaussian")
  plot(cv.lasso)
  
  
  cv.lasso$lambda.min
  
  cv.lasso$lambda.1se
  
  coef(cv.lasso, cv.lasso$lambda.min)
  
  coef(cv.lasso, cv.lasso$lambda.1se)
  
  # Final model with lambda.min
  lasso.model <- glmnet(x, y, alpha = 1, family = "gaussian",
                        lambda = cv.lasso$lambda.min)
  # Make prediction on test data
  x.test <- model.matrix(noof_mutations ~., test.data)[,-1]
  probabilities <- lasso.model %>% predict(newx = x.test)
  # predicted.classes <- ifelse(probabilities > 0.5, "pos", "neg")
  predicted.classes <- probabilities
  # Model accuracy
  observed.classes <- test.data$noof_mutations
  mean(predicted.classes == observed.classes)
  
  # Final model with lambda.1se
  lasso.model <- glmnet(x, y, alpha = 1, family = "gaussian",
                        lambda = cv.lasso$lambda.1se)
  # Make prediction on test data
  x.test <- model.matrix(noof_mutations ~., test.data)[,-1]
  probabilities <- lasso.model %>% predict(newx = x.test)
  # predicted.classes <- ifelse(probabilities > 0.5, "pos", "neg")
  predicted.classes <- probabilities
  # Model accuracy rate
  observed.classes <- test.data$noof_mutations
  mean(predicted.classes == observed.classes)
  
  
  # Fit the model
  full.model <- glm(noof_mutations ~., data = train.data, family = gaussian)
  # Make predictions
  probabilities <- full.model %>% predict(test.data, type = "response")
  # predicted.classes <- ifelse(probabilities > 0.5, "pos", "neg")
  predicted.classes <- probabilities
  # Model accuracy
  observed.classes <- test.data$noof_mutations
  mean(predicted.classes == observed.classes)
  
  predicted.and.compared <-cbind(observed.classes,predicted.classes)