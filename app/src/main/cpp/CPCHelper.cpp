#include "Internal.h"

const char hexmap[] = {'0', '1', '2', '3', '4', '5', '6', '7', '8', '9', 'A', 'B', 'C', 'D', 'E', 'F'};


bool CPCconvertImage(AndroidBitmapInfo *info, void *pixels, char **out)
{
    uint32_t iWidth = info->width;
    uint32_t iHeight = info->height;
    uint32_t iMonoSize = ((iWidth * iHeight) / 8);
    uint32_t iGreySize = iHeight * iWidth * sizeof(int16_t);
    void *pPtr = pixels;

    *out = nullptr;
    int16_t* bGreyImg = (int16_t*) malloc(iGreySize);
    if (bGreyImg == nullptr)
    {
        LOGE("Memory allocation failed (GreyImage)");
        return false;
    }
    int8_t* bMonoImg = (int8_t *) malloc(iMonoSize);
    if (bMonoImg == nullptr)
    {
        LOGE("Memory allocation failed (Mono Image)");
        free(bGreyImg);
        return false;
    }

    uint8_t bValue = 0;
    int oldpixel, newpixel, error;
    bool nbottom, nleft, nright;

    //RGB888 Format
    if (info->format == ANDROID_BITMAP_FORMAT_RGBA_8888)
    {
        for (int y=0; y < iHeight; y++)
        {
            uint32_t *line = (uint32_t *) pPtr;
            for (int x=0; x<iWidth; x++)
            {
                uint32_t col = line[x];
                int a = (col & 0xff000000 ) >> 24;
                int r = (col & 0xff0000 ) >> 16;
                int g = (col & 0x00ff00 ) >> 8;
                int b = (col & 0x0000ff );
                int m = (int)(0.299 * (double)r + 0.587 * (double)g + 0.114 * (double)b);
                m = ((a * m) + ((255-a) * 255)) / 255;
                if (m > 255) m = 255;
                if (m < 0) m = 0;
                bGreyImg[AT(x,y)] = m;
            }
            pPtr = (char *) pPtr + info->stride;
        }
    }
        // RGB565 Format
    else if (info->format == ANDROID_BITMAP_FORMAT_RGB_565)
    {
        for (int y=0; y < iHeight; y++)
        {
            uint16_t *line = (uint16_t *) pPtr;
            for (int x=0; x<iWidth; x++)
            {
                uint16_t col = line[x];
                int r = RED(col);
                int g = GREEN(col);
                int b = BLUE(col);
                int m = (int)(0.299 * (double)r + 0.587 * (double)g + 0.114 * (double)b);
                if (m > 255) m = 255;
                if (m < 0) m = 0;
                bGreyImg[AT(x,y)] = m;
            }
            pPtr = (char *) pPtr + info->stride;
        }
    }else {
        LOGE("Bitmap format not supported");
        free(bGreyImg);
        free(bMonoImg);
        return false;
    }

    // Dither the Image
    for (int y = 0; y < iHeight; y++)
    {
        nbottom = y < iHeight - 1;
        for (int x = 0; x < iWidth; x++)
        {
            nleft = x > 0;
            nright = x < iWidth - 1;

            oldpixel = bGreyImg[AT(x,y)];
            newpixel = oldpixel < 128 ? 0 : 255;
            bGreyImg[AT(x,y)] = newpixel;

            error = oldpixel - newpixel;
            if (nright) bGreyImg[AT(x+1,y)] += (int) (error * (7. / 16));
            if (nleft & nbottom) bGreyImg[AT(x-1,y+1)] += (int) (error * (3. / 16));
            if (nbottom) bGreyImg[AT(x,y+1)] += (int) (error * (5. / 16));
            if (nright && nbottom) bGreyImg[AT(x+1,y+1)] += (int) (error * (1. / 16));
        }
    }

    //Convert to Mono
    int iPos = 0;
    for (int y = 0; y < iHeight; y++)
    {
        for (int x = 0; x < iWidth; x++)
        {
            bValue <<= 1;
            if (bGreyImg[AT(x,y)] < 128) bValue |=1;
            if (iPos % 8 == 7) bMonoImg[iPos >> 3] = (bValue & 0xff);
            iPos++;
        }
    }

    //Free some space
    free(bGreyImg);

    char *outPtr = (char *) malloc((iMonoSize << 1) + 2);
    if (outPtr == nullptr)
    {
        LOGE("Memory allocation failed (CPC Data)");
        free(bGreyImg);
    }
    memset(outPtr,0,((iMonoSize << 1) + 2));
    *out = outPtr;

    //Return CPC data
    for (int p=0; p < iMonoSize; p++)
    {
        outPtr[p * 2] = hexmap[(bMonoImg[p] & 0xF0) >> 4];
        outPtr[p * 2 + 1] = hexmap[(bMonoImg[p] & 0x0F)];
    }
    free(bMonoImg);
    return true;
}