    library(tidyverse)
    library(caret)
    library(glmnet)
      
    # Load the data and remove NAs
    # data("centralities", package = "mlbench")
    # centralities <- na.omit(centralities)
    
     centralities <- read.table("/home/giray/Desktop/lasso/multiple output/centralitiesdiffersfull.csv",header=TRUE,sep=",")
     family <- "gaussian"
     output <- "noof_mutations"
     centralities$noof_resistances <- NULL
     centralities$no_of_full_mutations <- NULL
    # Inspect the data
    sample_n(centralities, 3)
    # Split the data into training and test set
    set.seed(123)
    training.samples <- centralities[,output] %>% 
      createDataPartition(p = 0.8, list = FALSE)
    train.data  <- centralities[training.samples, ]
    test.data <- centralities[-training.samples, ]
    
    
    
    # Dumy code categorical predictor variables
    x <- model.matrix(noof_mutations~., train.data)[,-1]
    # Convert the outcome (class) to a numerical variable
    # y <- ifelse(train.data[,output] == "pos", 1, 0)
    y <- train.data[,output] 
    
    
    
    glmnet(x, y, family = family, alpha = 1, lambda = NULL)
    
    
    
    library(glmnet)
    # Find the best lambda using cross-validation
    set.seed(123) 
    cv.lasso <- cv.glmnet(x, y, alpha = 1, family = family)
    # Fit the final model on the training data
    model <- glmnet(x, y, alpha = 1, family = family,
                    lambda = cv.lasso$lambda.min)
    # Display regression coefficients
    coef(model)
    # Make predictions on the test data
    x.test <- model.matrix(noof_mutations ~., test.data)[,-1]
    probabilities <- model %>% predict(newx = x.test)
    # predicted.classes <- ifelse(probabilities > 0.5, "pos", "neg")
    predicted.classes <- round(probabilities)
    # Model accuracy
    observed.classes <- test.data[,output]
    mean(abs(predicted.classes - observed.classes)<=1)
    
    
    
    library(glmnet)
    set.seed(123)
    cv.lasso <- cv.glmnet(x, y, alpha = 1, family = family)
    plot(cv.lasso)
    
    
    cv.lasso$lambda.min
    
    cv.lasso$lambda.1se
    
    coef(cv.lasso, cv.lasso$lambda.min)
    
    coef(cv.lasso, cv.lasso$lambda.1se)
    
    # Final model with lambda.min
    lasso.model <- glmnet(x, y, alpha = 1, family = family,
                          lambda = cv.lasso$lambda.min)
    # Make prediction on test data
    x.test <- model.matrix(noof_mutations ~., test.data)[,-1]
    probabilities <- lasso.model %>% predict(newx = x.test)
    # predicted.classes <- ifelse(probabilities > 0.5, "pos", "neg")
    predicted.classes <- round(probabilities)
    # Model accuracy
    observed.classes <- test.data[,output]
    mean(abs(predicted.classes - observed.classes)==2)
    mean(abs(predicted.classes - observed.classes)==1)
    mean(abs(predicted.classes - observed.classes)==0)
    
    # Final model with lambda.1se
    lasso.model <- glmnet(x, y, alpha = 1, family = family,
                          lambda = cv.lasso$lambda.1se)
    # Make prediction on test data
    x.test <- model.matrix(noof_mutations ~., test.data)[,-1]
    probabilities <- lasso.model %>% predict(newx = x.test)
    # predicted.classes <- ifelse(probabilities > 0.5, "pos", "neg")
    predicted.classes <- round(probabilities)
    # Model accuracy rate
    observed.classes <- test.data[,output]
    mean(abs(predicted.classes - observed.classes)==2)
    mean(abs(predicted.classes - observed.classes)==1)
    mean(abs(predicted.classes - observed.classes)==0)
    
    
    # Fit the model
    full.model <- glm(noof_mutations ~., data = train.data, family = family)
    # Make predictions
    probabilities <- full.model %>% predict(test.data, type = "response")
    # predicted.classes <- ifelse(probabilities > 0.5, "pos", "neg")
    predicted.classes <- round(probabilities)
    # Model accuracy
    observed.classes <- test.data[,output]
    mean(abs(predicted.classes - observed.classes)==2)
    mean(abs(predicted.classes - observed.classes)==1)
    mean(abs(predicted.classes - observed.classes)==0)
    
    predicted.and.compared <-cbind(observed.classes,predicted.classes)
    
    summary(full.model)