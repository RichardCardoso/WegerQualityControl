package com.richard.weger.wqc.service;

import android.content.Context;

import com.google.android.gms.common.util.Strings;
import com.richard.weger.wqc.R;
import com.richard.weger.wqc.helper.MessageboxHelper;
import com.richard.weger.wqc.result.ErrorResult;
import com.richard.weger.wqc.util.App;
import com.richard.weger.wqc.util.LoggerManager;

public abstract class ErrorResponseHandler {

    private static String handle(ErrorResult result){
        String message;
        if (!Strings.isEmptyOrWhitespace(result.getDescription())){
            message = result.getDescription();
        } else {
            message = App.getContext().getResources().getString(R.string.unknownErrorMessage);
        }
        message += "\n\nError code: " + result.getCode();
        message += "\nError level: " + result.getLevel();
        LoggerManager.log(ErrorResponseHandler.class, result);
        return message;
    }

    public static void handle(ErrorResult result, Context delegate, MessageboxHelper.Method method){
        String message;
        message = handle(result);
        MessageboxHelper.showMessage(delegate, message, delegate.getResources().getString(R.string.okTag), method);
    }

    public static void handle(ErrorResult result, Context delegate, String positiveTag, String negativeTag, MessageboxHelper.Method positiveMethod, MessageboxHelper.Method negativeMethod){
        String message;
        message = handle(result);
        MessageboxHelper.showMessage(delegate, null, message, positiveTag, negativeTag, positiveMethod, negativeMethod);
    }
}