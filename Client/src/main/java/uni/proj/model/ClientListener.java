package uni.proj.model;

import uni.proj.model.protocol.data.ErrorData;
import uni.proj.model.protocol.data.ResponseData;

public interface ClientListener {

    void onResponse(ResponseData response);
    void onError(ErrorData error);
}
