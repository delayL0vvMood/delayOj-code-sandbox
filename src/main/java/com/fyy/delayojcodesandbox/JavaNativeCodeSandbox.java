package com.fyy.delayojcodesandbox;
import com.fyy.delayojcodesandbox.model.ExecuteCodeRequest;
import com.fyy.delayojcodesandbox.model.ExecuteCodeResponse;
import org.springframework.stereotype.Component;
@Component
public class JavaNativeCodeSandbox extends JavaCodeSandboxTemplate implements CodeSandbox {
    @Override
    public ExecuteCodeResponse executeCode(ExecuteCodeRequest executeCodeRequest) {
        return super.executeCode(executeCodeRequest);
    }
}
