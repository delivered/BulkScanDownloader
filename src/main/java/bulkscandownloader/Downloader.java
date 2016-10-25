package bulkscandownloader;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.MalformedURLException;
import java.nio.file.Paths;
import java.rmi.Remote;
import java.rmi.RemoteException;

import javax.ws.rs.core.Cookie;
import javax.ws.rs.core.Response;
import javax.xml.rpc.ServiceException;
import javax.xml.soap.SOAPException;

import org.apache.axis.types.URI;
import org.apache.axis.types.URI.MalformedURIException;
import org.apache.commons.io.IOUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import iris.*;
import iris.containers.ArrayDictionary;

public class Downloader {

    private static final Logger logger = LogManager.getLogger(Downloader.class);

    private Configuration config;
    private Authentication authClient;
    private ContentDownloadService downloadService;

    public Downloader(Configuration config) throws ServiceException {

        this.authClient = new Authentication(config.getFrontSessionURL());
        this.downloadService = authClient.getDownloadService(config.getDownloadServiceURL());
        this.config = config;
    }

    public void run() throws InterruptedException {
        while (true) {
            logger.info("Starting fetch iteration.");
            oneIteration();
            logger.info("Ending fetch iteration, sleeping 10 minutes now.");
            Thread.sleep(600000); // 10 minutes
        }
    }

    public void oneIteration() {

        for (User user : config.getUsers()) {
            if (!user.isActive())
                continue;

            QueryDownloadableContentRequest downloadRequest = new QueryDownloadableContentRequest(
                    user.isAllEnvelopeSides(),
                    user.isContentScans(),
                    user.isEnvelopeImages(),
                    null);  // MailboxID, can be left at null to grab all mailboxes

            try {
                authClient.signIn(user.getUsername(), user.getPassword());
                downloadItemsForUser(user, downloadRequest);
            } catch (Exception e) {
                logger.warn("Unable to sign in user: " + user.getUsername());
                logger.warn(e);
                // If we can't sign in, proceed to next user
            }
        }
    }

    private void downloadItemsForUser(User user, QueryDownloadableContentRequest downloadRequest) {
        QueryDownloadableContentResponse qdcr = null;
        int totalJobCompletedCount = 0;

        while (qdcr == null || sumOfLengthsOfLists(qdcr.getQueryResult()) < qdcr.getItemCount()) {
            try {
                qdcr = requestDownloadDance(user, downloadRequest);
            } catch (Exception e) {
                logger.warn("Unable to create a download request for user: " + user.getUsername());
                logger.warn("Proceeding to next user.");
                break; // We can't make a download request for this user
            }

            if (qdcr.getItemCount() == 0)
                logger.info("No items to download for user: " + user.getUsername());

            ArrayDictionary[] scannedContentDictionary = qdcr.getQueryResult();
            int currentListCompletedCount = 0;

            for (ArrayDictionary kvPair : scannedContentDictionary) {
                DownloadableContent[] scannedContentList = kvPair.getValue();

                Enclosure firstEnclosure = null;

                for (DownloadableContent sc : scannedContentList) {

                    try {
                        processItem(user, sc, qdcr.getItemCount() - (currentListCompletedCount + 1));
                        ++currentListCompletedCount;
                        ++totalJobCompletedCount;
                        if (sc.getClass() == ScannedContent.class) {
                            acknowledgeDownload(sc);
                        } else if (firstEnclosure == null && sc.getClass() == Enclosure.class) {
                            firstEnclosure = (Enclosure) sc;
                        }
                    } catch (IOException e) {
                        // Just log this error as we want to be able to continue with the other
                        // users and items even if this fails.
                        logger.error("Unable save item to disk:");
                        logger.error(e);
                    }
                }

                if (firstEnclosure != null) {
                    acknowledgeDownload(firstEnclosure);
                }
                if (scannedContentList.length > 0) {
                    processPostDownloadAction(scannedContentList[0]);
                }
            }
        }
        if (totalJobCompletedCount > 0)
            logger.info("Completed downloading for user: " + user.getUsername());
    }

    /*
     * This dance happens because a series of item downloads may take so long that the authentication
     * session will time out in the middle of the process so it needs to be renewed before the next
     * item can be downloaded.
     */
    private QueryDownloadableContentResponse requestDownloadDance(User user, QueryDownloadableContentRequest downloadRequest) throws Exception {
        QueryDownloadableContentResponse qdcr;
        try {
            qdcr = downloadService.queryDownloadableContent(downloadRequest);
        } catch (AuthFault e) { // retry once in case session expired
            authClient.signIn(user.getUsername(), user.getPassword());
            qdcr = downloadService.queryDownloadableContent(downloadRequest);
        }
        return qdcr;
    }

    private void processPostDownloadAction(DownloadableContent content) {
        try {
            if (content.getItemStatus() == PItemStatus.Active &&
                    (content.getOperation() == null ||
                            content.getExtractStatus() == ExtractStatus.Canceled ||
                            content.getExtractStatus() == ExtractStatus.Completed
                    )) {

                switch (config.getPostDownloadAction()) {
                    case 0: //Noop
                        break;

                    case 1: // Recycle
                        RecycleDownloadedItemRequest recycleRequest = new RecycleDownloadedItemRequest(
                                true, // Keep Scans
                                content.getMailboxID(),
                                new VItemID[]{content.getVItemID()}
                        );
                        downloadService.recycleDownloadedItem(recycleRequest);
                        break;

                    case 2: // Shred
                        ShredDownloadedItemRequest shredRequest = new ShredDownloadedItemRequest(
                                true, // Keep Scans
                                content.getMailboxID(),
                                new VItemID[]{content.getVItemID()}
                        );
                        downloadService.shredDownloadedItem(shredRequest);
                        break;

                    default:
                        logger.error("Post Download Action not properly defined. Doing nothing.");
                }
            }
        } catch (RemoteException e) {
            logger.error("Unable to contact download service for post download action, no action will be taken.");
            logger.error(e);
        }
    }

    private AcknowledgeDownloadResponse acknowledgeDownload(DownloadableContent sc) {
        if (BulkScanDownloader.DEBUG_BUILD) // Don't acknowledge for debug builds, we want to be able to re-run things
            return null;

        AcknowledgeDownloadResponse response;
        try {
            response = downloadService.acknowledgeDownload(new AcknowledgeDownloadRequest(sc));
        } catch (RemoteException e) {
            logger.error("Unable to contact download service for download acknowledgement, item will be downloaded again next time.");
            logger.error(e);
            response = null;
        }
        return response;
    }

    private void processItem(User user, DownloadableContent itemToSave, int itemsRemaining) throws IOException {
        String fileName = getFileName(user, itemToSave);
        logger.info("File processing started: " + fileName);

        saveScannedContent(itemToSave, fileName);

        logger.info("File processing completed: " + fileName);
        logger.info("Items remaining: " + itemsRemaining);
    }

    private void saveScannedContent(DownloadableContent contentToRetrieve, String fileName) throws IOException {
        File file = new File(fileName);
        file.getParentFile().mkdirs();
        file.createNewFile();
        InputStream inputStream = fetchScannedContent(contentToRetrieve);

        OutputStream outputStream = new FileOutputStream(file);

        IOUtils.copy(inputStream, outputStream);

        inputStream.close();
        outputStream.close();
    }

    private InputStream fetchScannedContent(DownloadableContent contentToRetrieve) throws RemoteException {

        FetchDownloadableContentRequest fetchRequest = new FetchDownloadableContentRequest(contentToRetrieve);
        URI url = downloadService.fetchDownloadableContent(fetchRequest).getUrl();

        logger.info("Starting fetch of: " + url.toString());

        Cookie cookie = new Cookie("AuthFront", authClient.getSession().getSessionID(), "/", url.getHost());

        Response dataResponse = this.authClient.client.target(url.toString()).request().cookie(cookie).get();
        return dataResponse.readEntity(InputStream.class);
    }

    private String getFileName(User user, DownloadableContent itemToSave) {
        String dir = config.getRootDir();

        if (config.isUseFolders()) {
            dir = Paths.get(dir, user.getUsername(), itemToSave.getFolderName()).toString();
        } else {
            dir = Paths.get(dir, user.getUsername()).toString();
        }
        Enclosure enclosureToSave = itemToSave.getClass() == Enclosure.class ? (Enclosure) itemToSave : null;

        String fileName = String.format("%s%s.%s",
                itemToSave.getLicensePlate().getID(),
                enclosureToSave != null ? "-" + enclosureToSave.getSide().toString() : "",
                itemToSave.getContentType() == FileStoreContentType.JPEG ? "JPG" : itemToSave.getContentType().toString()
        );
        return Paths.get(dir, fileName).toString();
    }

    private Integer sumOfLengthsOfLists(
            ArrayDictionary[] queryResult) {
        int sum = 0;
        for (ArrayDictionary content : queryResult) sum += content.getValue().length;
        return sum;
    }

}
