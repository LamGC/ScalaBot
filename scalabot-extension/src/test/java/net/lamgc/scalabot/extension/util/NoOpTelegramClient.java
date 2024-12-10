package net.lamgc.scalabot.extension.util;

import org.telegram.telegrambots.meta.api.methods.botapimethods.BotApiMethod;
import org.telegram.telegrambots.meta.api.methods.groupadministration.SetChatPhoto;
import org.telegram.telegrambots.meta.api.methods.send.*;
import org.telegram.telegrambots.meta.api.methods.stickers.*;
import org.telegram.telegrambots.meta.api.methods.updates.SetWebhook;
import org.telegram.telegrambots.meta.api.methods.updatingmessages.EditMessageMedia;
import org.telegram.telegrambots.meta.api.objects.File;
import org.telegram.telegrambots.meta.api.objects.message.Message;
import org.telegram.telegrambots.meta.generics.TelegramClient;

import java.io.InputStream;
import java.io.Serializable;
import java.util.List;
import java.util.concurrent.CompletableFuture;

class NoOpTelegramClient implements TelegramClient {

    @Override
    public <T extends Serializable, Method extends BotApiMethod<T>> CompletableFuture<T> executeAsync(Method method) {
        return null;
    }

    @Override
    public <T extends Serializable, Method extends BotApiMethod<T>> T execute(Method method) {
        return null;
    }

    @Override
    public Message execute(SendDocument sendDocument) {
        return null;
    }

    @Override
    public Message execute(SendPhoto sendPhoto) {
        return null;
    }

    @Override
    public Boolean execute(SetWebhook setWebhook) {
        return null;
    }

    @Override
    public Message execute(SendVideo sendVideo) {
        return null;
    }

    @Override
    public Message execute(SendVideoNote sendVideoNote) {
        return null;
    }

    @Override
    public Message execute(SendSticker sendSticker) {
        return null;
    }

    @Override
    public Message execute(SendAudio sendAudio) {
        return null;
    }

    @Override
    public Message execute(SendVoice sendVoice) {
        return null;
    }

    @Override
    public List<Message> execute(SendMediaGroup sendMediaGroup) {
        return List.of();
    }

    @Override
    public List<Message> execute(SendPaidMedia sendPaidMedia) {
        return List.of();
    }

    @Override
    public Boolean execute(SetChatPhoto setChatPhoto) {
        return null;
    }

    @Override
    public Boolean execute(AddStickerToSet addStickerToSet) {
        return null;
    }

    @Override
    public Boolean execute(ReplaceStickerInSet replaceStickerInSet) {
        return null;
    }

    @Override
    public Boolean execute(SetStickerSetThumbnail setStickerSetThumbnail) {
        return null;
    }

    @Override
    public Boolean execute(CreateNewStickerSet createNewStickerSet) {
        return null;
    }

    @Override
    public File execute(UploadStickerFile uploadStickerFile) {
        return null;
    }

    @Override
    public Serializable execute(EditMessageMedia editMessageMedia) {
        return null;
    }

    @Override
    public java.io.File downloadFile(File file) {
        return null;
    }

    @Override
    public InputStream downloadFileAsStream(File file) {
        return null;
    }

    @Override
    public Message execute(SendAnimation sendAnimation) {
        return null;
    }

    @Override
    public CompletableFuture<Message> executeAsync(SendDocument sendDocument) {
        return null;
    }

    @Override
    public CompletableFuture<Message> executeAsync(SendPhoto sendPhoto) {
        return null;
    }

    @Override
    public CompletableFuture<Boolean> executeAsync(SetWebhook setWebhook) {
        return null;
    }

    @Override
    public CompletableFuture<Message> executeAsync(SendVideo sendVideo) {
        return null;
    }

    @Override
    public CompletableFuture<Message> executeAsync(SendVideoNote sendVideoNote) {
        return null;
    }

    @Override
    public CompletableFuture<Message> executeAsync(SendSticker sendSticker) {
        return null;
    }

    @Override
    public CompletableFuture<Message> executeAsync(SendAudio sendAudio) {
        return null;
    }

    @Override
    public CompletableFuture<Message> executeAsync(SendVoice sendVoice) {
        return null;
    }

    @Override
    public CompletableFuture<List<Message>> executeAsync(SendMediaGroup sendMediaGroup) {
        return null;
    }

    @Override
    public CompletableFuture<List<Message>> executeAsync(SendPaidMedia sendPaidMedia) {
        return null;
    }

    @Override
    public CompletableFuture<Boolean> executeAsync(SetChatPhoto setChatPhoto) {
        return null;
    }

    @Override
    public CompletableFuture<Boolean> executeAsync(AddStickerToSet addStickerToSet) {
        return null;
    }

    @Override
    public CompletableFuture<Boolean> executeAsync(ReplaceStickerInSet replaceStickerInSet) {
        return null;
    }

    @Override
    public CompletableFuture<Boolean> executeAsync(SetStickerSetThumbnail setStickerSetThumbnail) {
        return null;
    }

    @Override
    public CompletableFuture<Boolean> executeAsync(CreateNewStickerSet createNewStickerSet) {
        return null;
    }

    @Override
    public CompletableFuture<File> executeAsync(UploadStickerFile uploadStickerFile) {
        return null;
    }

    @Override
    public CompletableFuture<Serializable> executeAsync(EditMessageMedia editMessageMedia) {
        return null;
    }

    @Override
    public CompletableFuture<Message> executeAsync(SendAnimation sendAnimation) {
        return null;
    }

    @Override
    public CompletableFuture<java.io.File> downloadFileAsync(File file) {
        return null;
    }

    @Override
    public CompletableFuture<InputStream> downloadFileAsStreamAsync(File file) {
        return null;
    }
}
